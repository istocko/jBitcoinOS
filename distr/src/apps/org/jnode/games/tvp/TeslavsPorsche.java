/*
 * $Id$
 *
 * Copyright (C) 2003-2015 JNode.org
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public 
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; If not, write to the Free Software Foundation, Inc., 
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
 
package org.jnode.games.tvp;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;


public class TeslavsPorsche extends JComponent implements KeyListener, MouseListener {
    
    
    /**
	 * 
	 */
	private static final long serialVersionUID = 7402145517874094127L;

	

    private final Runnable runRepaint = new Runnable() {
        public void run() {
            repaint();
        }
    };

    TeslavsPorsche() {
        setOpaque(false);
        
        addKeyListener(this);
        addMouseListener(this);
        setFocusable(true);
        setRequestFocusEnabled(true);
        enableEvents(AWTEvent.FOCUS_EVENT_MASK);
        enableEvents(AWTEvent.KEY_EVENT_MASK);
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    }

   

    

    /**
     * Update the screen.
     *
     * @param g the graphics context
     * @see javax.swing.JComponent#update(java.awt.Graphics)
     */
    public void update(Graphics g) {
        paint(g);
    }

    /**
     * Paint the game graphics.
     * @param g the graphics context
     * @see javax.swing.JComponent#paint(java.awt.Graphics)
     */
    public void paint(Graphics g) {
        
    }

    

    /**
     * Handle keys.
     * @param e the key event
     */
    public void keyPressed(KeyEvent e) {
        int kc = e.getKeyCode();
        if (kc == KeyEvent.VK_N) {
            newGame();
            return;
        }
        if (kc == KeyEvent.VK_P) {
            
            return;
        }
        
        SwingUtilities.invokeLater(runRepaint);
    }

    private void newGame() {
        
       
        requestFocus();
        
       
       
    }

    

    
    /**
     * Handle mouse input.
     */
    public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            if (this.contains(e.getX(), e.getY())) {
                if (!this.hasFocus() && this.isRequestFocusEnabled()) {
                    this.requestFocus();
                }
            }
        }
    }

    /**
     * Unused.
     */
    public void keyReleased(KeyEvent e) {

    }

    /**
     * Unused.
     */
    public void keyTyped(KeyEvent e) {

    }

    /**
     * Unused.
     */
    public void mouseClicked(MouseEvent e) {

    }

    /**
     * Unused.
     */
    public void mouseEntered(MouseEvent e) {

    }

    /**
     * Unused.
     */
    public void mouseExited(MouseEvent e) {

    }

    /**
     * Unused.
     */
    public void mouseReleased(MouseEvent e) {

    }

    /**
     * Start Tetris.
     */
    public static void main(final String[] argv) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                
                JFrame frame = new JFrame("Tesla vs. Porsche East Slovakia Championship");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                final TeslavsPorsche tvp = new TeslavsPorsche();
                frame.add(tvp, BorderLayout.CENTER);
                frame.setSize(600,800);
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH); 
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                       
                    }
                });
                frame.setVisible(true);
                tvp.requestFocus();
                tvp.newGame();
            }
        });
    }

    
}
