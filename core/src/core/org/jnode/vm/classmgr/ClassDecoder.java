/**
 * $Id$
 */

package org.jnode.vm.classmgr;

import org.jnode.system.BootLog;

public final class ClassDecoder {

    // ------------------------------------------
    // VM ClassLoader Code
    // ------------------------------------------

    private static char[] ConstantValueAttrName;

    private static char[] CodeAttrName;

    private static char[] ExceptionsAttrName;

    private static char[] LineNrTableAttrName;

    //private static final Logger log = Logger.getLogger(ClassDecoder.class);

    private static final void cl_init() {
        if (ConstantValueAttrName == null) {
            ConstantValueAttrName = "ConstantValue".toCharArray();
        }
        if (CodeAttrName == null) {
            CodeAttrName = "Code".toCharArray();
        }
        if (ExceptionsAttrName == null) {
            ExceptionsAttrName = "Exceptions".toCharArray();
        }
        if (LineNrTableAttrName == null) {
            LineNrTableAttrName = "LineNumberTable".toCharArray();
        }
    }

    /**
     * Convert a class-file image into a Class object. Steps taken in this
     * phase: 1. Decode the class-file image (CLS_LS_DECODED) 2. Load the
     * super-class of the loaded class (CLS_LS_DEFINED) 3. Link the class so
     * that the VMT is set and the offset of the non-static fields are set
     * correctly.
     * 
     * @param className
     * @param data
     * @param offset
     * @param class_image_length
     * @param rejectNatives
     * @param clc
     * @param selectorMap
     * @return The defined class
     */
    public static final VmType defineClass(String className, byte[] data,
            int offset, int class_image_length, boolean rejectNatives,
            VmClassLoader clc, SelectorMap selectorMap, VmStatics statics) {
        cl_init();
        VmType cls = decodeClass(data, offset, class_image_length,
                rejectNatives, clc, selectorMap, statics);
        return cls;
    }

    /**
     * Decode a given class.
     * 
     * @param data
     * @param offset
     * @param class_image_length
     * @param rejectNatives
     * @param clc
     * @param selectorMap
     * @return The decoded class
     * @throws ClassFormatError
     */
    private static final VmType decodeClass(byte[] data, int offset,
            int class_image_length, boolean rejectNatives, VmClassLoader clc,
            SelectorMap selectorMap, VmStatics statics) throws ClassFormatError {
        if (data == null) { throw new ClassFormatError(
                "ClassDecoder.decodeClass: data==null"); }
        final ClassReader reader = new ClassReader(data, offset, class_image_length);
        final int slotSize = clc.getArchitecture().getReferenceSize();

        final int magic = reader.readu4();
        if (magic != 0xCAFEBABE) { throw new ClassFormatError("invalid magic"); }
        final int min_version = reader.readu2();
        final int maj_version = reader.readu2();

        if (false) {
            BootLog.debug("Class file version " + maj_version + ";"
                    + min_version);
        }

        final int cpcount = reader.readu2();
        // allocate enough space for the CP
        final byte[] tags = new byte[ cpcount];
        final VmCP cp = new VmCP(cpcount, tags);
        for (int i = 1; i < cpcount; i++) {
            final int tag = reader.readu1();
            tags[ i] = (byte) tag;
            switch (tag) {
            case 1:
                // Utf8
                cp.setUTF8(i, new String(reader.readUTF()));
                break;
            case 3:
                // int
                cp.setInt(i, reader.readu4());
                break;
            case 4:
                // float
                cp.setInt(i, reader.readu4());
                break;
            case 5:
                // long
                cp.setLong(i, reader.readu8());
                i++;
                break;
            case 6:
                // double
                cp.setLong(i, reader.readu8());
                i++;
                break;
            case 7:
                // class
                cp.setConstClass(i, new VmConstClass(cp, i,
                        reader.readu2()));
                break;
            case 8:
                // String
                cp.setInt(i, reader.readu2());
                break;
            case 9:
                // Fieldref
                {
                    final int clsIdx = reader.readu2();
                    final int ntIdx = reader.readu2();
                    cp.setConstFieldRef(i, new VmConstFieldRef(cp, i, clsIdx,
                            ntIdx));
                }
                break;
            case 10:
                // Methodref
                {
                    final int clsIdx = reader.readu2();
                    final int ntIdx = reader.readu2();
                    cp.setConstMethodRef(i, new VmConstMethodRef(cp, i, clsIdx,
                            ntIdx));
                }
                break;
            case 11:
                // IMethodref
                {
                    final int clsIdx = reader.readu2();
                    final int ntIdx = reader.readu2();
                    cp.setConstIMethodRef(i, new VmConstIMethodRef(cp, i,
                            clsIdx, ntIdx));
                }
                break;
            case 12:
                // Name and Type
                {
                    final int nIdx = reader.readu2();
                    final int dIdx = reader.readu2();
                    cp.setConstNameAndType(i, new VmConstNameAndType(cp, nIdx,
                            dIdx));
                }
                break;
            default:
                throw new ClassFormatError("Invalid constantpool tag: "
                        + tags[ i]);
            }
        }

        // Now patch the required entries
        for (int i = 1; i < cpcount; i++) {
            switch (tags[ i]) {
            case 8:
                // String
                final int idx = cp.getInt(i);
                final int staticsIdx = statics.allocConstantStringField(cp.getUTF8(idx));
                cp.setString(i, new VmConstString(cp, i, staticsIdx));
                break;
            }
        }

        final int classModifiers = reader.readu2();

        final VmConstClass this_class = cp.getConstClass(reader.readu2());
        final String clsName = this_class.getClassName();

        final VmConstClass super_class = cp.getConstClass(reader.readu2());
        final String superClassName;
        if (super_class != null) {
            superClassName = super_class.getClassName();
        } else {
            superClassName = null;
        }

        // Allocate the class object
        final VmType cls;
        if (Modifier.isInterface(classModifiers)) {
            cls = new VmInterfaceClass(clsName, superClassName, clc,
                    classModifiers);
        } else {
            cls = new VmNormalClass(clsName, superClassName, clc,
                    classModifiers);
        }
        cls.setCp(cp);

        // Interface table
        readInterfaces(reader, cls, cp);

        // Field table
        readFields(reader, cls, cp, statics, slotSize);

        // Method Table
        readMethods(reader, rejectNatives, cls, cp, selectorMap, statics);

        return cls;
    }

    /**
     * Read the interfaces table
     * 
     * @param reader
     * @param cls
     * @param cp
     */
    private static void readInterfaces(ClassReader reader, VmType cls,
            VmCP cp) {
        final int icount = reader.readu2();
        if (icount > 0) {
            final VmImplementedInterface[] itable = new VmImplementedInterface[ icount];
            for (int i = 0; i < icount; i++) {
                final VmConstClass icls = cp
                        .getConstClass(reader.readu2());
                itable[ i] = new VmImplementedInterface(icls.getClassName());
            }
            cls.setInterfaceTable(itable);
        }
    }

    /**
     * Read the fields table
     * 
     * @param reader
     * @param cls
     * @param cp
     * @param slotSize
     */
    private static void readFields(ClassReader reader, VmType cls, VmCP cp,
            VmStatics statics, int slotSize) {
        final int fcount = reader.readu2();
        if (fcount > 0) {
            final VmField[] ftable = new VmField[ fcount];

            int objectSize = 0;
            for (int i = 0; i < fcount; i++) {
                final boolean wide;
                int modifiers = reader.readu2();
                final String name = cp.getUTF8(reader.readu2());
                final String signature = cp.getUTF8(reader.readu2());
                switch (signature.charAt(0)) {
                case 'J':
                case 'D':
                    modifiers = modifiers | Modifier.ACC_WIDE;
                    wide = true;
                    break;
                default:
                    wide = false;
                }
                final boolean isstatic = (modifiers & Modifier.ACC_STATIC) != 0;
                final int staticsIdx;
                final VmField fs;
                if (isstatic) {
                    // If static allocate space for it.
                    switch (signature.charAt(0)) {
                    case 'B':
                        staticsIdx = statics.allocIntField();
                        break;
                    case 'C':
                        staticsIdx = statics.allocIntField();
                        break;
                    case 'D':
                        staticsIdx = statics.allocLongField();
                        break;
                    case 'F':
                        staticsIdx = statics.allocIntField();
                        break;
                    case 'I':
                        staticsIdx = statics.allocIntField();
                        break;
                    case 'J':
                        staticsIdx = statics.allocLongField();
                        break;
                    case 'S':
                        staticsIdx = statics.allocIntField();
                        break;
                    case 'Z':
                        staticsIdx = statics.allocIntField();
                        break;
                    default:
                        {
                            if (Modifier.isAddressType(signature)) {
                                staticsIdx = statics.allocAddressField();
                            } else {
                                staticsIdx = statics.allocObjectField();
                                //System.out.println(NumberUtils.hex(staticsIdx)
                                // + "\t" + cls.getName() + "." + name);
                            }
                        }
                        break;
                    }
                    fs = new VmStaticField(name, signature, modifiers,
                            staticsIdx, cls, slotSize);
                } else {
                    staticsIdx = -1;
                    final int fieldOffset;
                    // Set the offset (keep in mind that this will be fixed
                    // by ClassResolver with respect to the objectsize of the
                    // super-class.
                    fieldOffset = objectSize;
                    // Increment the objectSize
                    if (wide)
                        objectSize += 8;
                    else
                        objectSize += 4;
                    fs = new VmInstanceField(name, signature, modifiers,
                            fieldOffset, cls, slotSize);
                }
                ftable[ i] = fs;

                // Read field attributes
                final int acount = reader.readu2();
                for (int a = 0; a < acount; a++) {
                    final String attrName = cp.getUTF8(reader.readu2());
                    final int length = reader.readu4();
                    if (isstatic
                            && VmArray.equals(ConstantValueAttrName, attrName)) {
                        final int idx = reader.readu2();
                        switch (signature.charAt(0)) {
                        case 'B':
                            statics.setInt(staticsIdx, cp.getInt(idx));
                            break;
                        case 'C':
                            statics.setInt(staticsIdx, cp.getInt(idx));
                            break;
                        case 'D':
                            statics.setLong(staticsIdx, cp.getLong(idx));
                            break;
                        case 'F':
                            statics.setInt(staticsIdx, cp.getInt(idx));
                            break;
                        case 'I':
                            statics.setInt(staticsIdx, cp.getInt(idx));
                            break;
                        case 'J':
                            statics.setLong(staticsIdx, cp.getLong(idx));
                            break;
                        case 'S':
                            statics.setInt(staticsIdx, cp.getInt(idx));
                            break;
                        case 'Z':
                            statics.setInt(staticsIdx, cp.getInt(idx));
                            break;
                        default:
                            //throw new IllegalArgumentException("signature "
                            // + signature);
                            statics.setObject(staticsIdx, cp.getString(idx));
                            break;
                        }
                    } else {
                        reader.skip(length);
                    }
                }
            }
            cls.setFieldTable(ftable);
            if (objectSize > 0) {
                ((VmNormalClass) cls).setObjectSize(objectSize);
            }
        }
    }

    /**
     * Read the method table
     * 
     * @param reader
     * @param rejectNatives
     * @param cls
     * @param cp
     * @param selectorMap
     */
    private static void readMethods(ClassReader reader, boolean rejectNatives,
            VmType cls, VmCP cp, SelectorMap selectorMap,
            VmStatics statics) {
        final int mcount = reader.readu2();
        if (mcount > 0) {
            final VmMethod[] mtable = new VmMethod[ mcount];

            for (int i = 0; i < mcount; i++) {
                final int modifiers = reader.readu2();
                final String name = cp.getUTF8(reader.readu2());
                final String signature = cp.getUTF8(reader.readu2());
                int argSlotCount = Signature.getArgSlotCount(signature);
                final boolean isStatic = ((modifiers & Modifier.ACC_STATIC) != 0);

                if ((modifiers & Modifier.ACC_STATIC) == 0) {
                    argSlotCount++; // add the "this" argument
                }

                final VmMethod mts;
                final boolean isSpecial = name.equals("<init>");
                final int staticsIdx = statics.allocMethod();
                if (isStatic || isSpecial) {
                    if (isSpecial) {
                        mts = new VmSpecialMethod(name, signature, modifiers,
                                cls, argSlotCount, staticsIdx);
                    } else {
                        mts = new VmStaticMethod(name, signature, modifiers,
                                cls, argSlotCount, staticsIdx);
                    }
                } else {
                    mts = new VmInstanceMethod(name, signature, modifiers, cls,
                            argSlotCount, selectorMap, staticsIdx);
                }
                statics.setMethod(staticsIdx, mts);
                mtable[ i] = mts;

                // Read methods attributes
                final int acount = reader.readu2();
                for (int a = 0; a < acount; a++) {
                    String attrName = cp.getUTF8(reader.readu2());
                    int length = reader.readu4();
                    if (VmArray.equals(CodeAttrName, attrName)) {
                        mts.setBytecode(readCode(reader, cls, cp, mts));
                    } else if (VmArray.equals(ExceptionsAttrName, attrName)) {
                        mts.setExceptions(readExceptions(reader, cls, cp));
                    } else {
                        reader.skip(length);
                    }
                }
                if ((modifiers & Modifier.ACC_NATIVE) != 0) {
                    if (rejectNatives) { throw new ClassFormatError(
                            "Native method " + mts); }
                }
            }
            cls.setMethodTable(mtable);
        }
    }

    /**
     * Decode the data of a code-attribute
     * 
     * @param reader
     * @param cls
     * @param cp
     * @param method
     * @return The read code
     */
    private static final VmByteCode readCode(ClassReader reader,
            VmType cls, VmCP cp, VmMethod method) {

        final int maxStack = reader.readu2();
        final int noLocals = reader.readu2();
        final int codelength = reader.readu4();
        final byte[] code = reader.readBytes(codelength);

        // Read the exception Table
        final int ecount = reader.readu2();
        final VmInterpretedExceptionHandler[] etable = new VmInterpretedExceptionHandler[ ecount];
        for (int i = 0; i < ecount; i++) {
            final int startPC = reader.readu2();
            final int endPC = reader.readu2();
            final int handlerPC = reader.readu2();
            final int catchType = reader.readu2();
            etable[ i] = new VmInterpretedExceptionHandler(cp, startPC, endPC,
                    handlerPC, catchType);
        }

        // Read the attributes
        VmLineNumberMap lnTable = null;
        final int acount = reader.readu2();
        for (int i = 0; i < acount; i++) {
            final String attrName = cp.getUTF8(reader.readu2());
            final int len = reader.readu4();
            if (VmArray.equals(LineNrTableAttrName, attrName)) {
                lnTable = readLineNrTable(reader);
            } else {
                reader.skip(len);
            }
        }

        return new VmByteCode(method, code, noLocals, maxStack, etable, lnTable);
    }

    /**
     * Decode the data of a Exceptions attribute
     * 
     * @param reader
     * @param cls
     * @param cp
     * @return The read exceptions
     */
    private static final VmExceptions readExceptions(ClassReader reader,
            VmType cls, VmCP cp) {

        // Read the exceptions
        final int ecount = reader.readu2();
        final VmConstClass[] list = new VmConstClass[ ecount];
        for (int i = 0; i < ecount; i++) {
            final int idx = reader.readu2();
            list[ i] = cp.getConstClass(idx);
        }

        return new VmExceptions(list);
    }

    /**
     * Decode the data of a LineNumberTable-attribute
     * 
     * @param reader
     * @return The line number map
     */
    private static final VmLineNumberMap readLineNrTable(ClassReader reader) {
        final int len = reader.readu2();
        final char[] lnTable = new char[ len * VmLineNumberMap.LNT_ELEMSIZE];

        for (int i = 0; i < len; i++) {
            final int ofs = i * VmLineNumberMap.LNT_ELEMSIZE;
            lnTable[ ofs + VmLineNumberMap.LNT_STARTPC_OFS] = (char) reader.readu2();
            lnTable[ ofs + VmLineNumberMap.LNT_LINENR_OFS] = (char) reader.readu2();
        }

        return new VmLineNumberMap(lnTable);
    }
}
