/*
 * @(#)NervousText.java	1.4 01/12/10
 *
 * Copyright 2002 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import java.awt.event.*;
import java.awt.Graphics;
import java.awt.Font;
import java.applet.Applet;

/**
 * An applet that displays jittering text on the screen.
 *
 * @author Daniel Wyszynski 04/12/95
 * @version 1.10, 02/05/97
 * @modified 05/09/95 kwalrath Changed string; added thread suspension
 * @modified 02/06/98 madbot removed use of suspend and resume and cleaned up
 */

public class NervousText extends Applet implements Runnable, MouseListener {
    String banner;		// The text to be displayed
    char bannerChars[];		// The same text as an array of characters
    Thread runner = null;	// The thread that is displaying the text
    boolean threadSuspended;	// True when thread suspended (via mouse click)

    public void init() {
	banner = getParameter("text");
	if (banner == null) {
	    banner = "HotJava";
	}
        int bannerLength = banner.length();
	bannerChars =  new char[bannerLength];
        banner.getChars(0, banner.length(), bannerChars, 0);

        threadSuspended = false;

	resize(15*(bannerLength + 1), 50);
	setFont(new Font("TimesRoman", Font.BOLD, 36));
	addMouseListener(this);
    }

    public void destroy() {
        removeMouseListener(this);
    }

    public void start() {
        runner = new Thread(this);
        runner.start();
    }

    public synchronized void stop() {
	runner = null;
        if (threadSuspended) {
            threadSuspended = false;
            notify();
        }
    }

    public void run() {
        Thread me = Thread.currentThread();
        while (runner == me) {
            try {
                Thread.sleep(100);
                synchronized(this) {
                    while (threadSuspended) {
                        wait();
                    }
                }
            } catch (InterruptedException e){
            }
            repaint();
        }
    }

    public void paint(Graphics g) {
        for(int i=0, length = banner.length(); i<length; i++) {
            int x = (int) (10*Math.random() + 15*i);
            int y = (int) (10*Math.random() + 36);
            g.drawChars(bannerChars, i, 1, x, y);
	}
    }

    public synchronized void mousePressed(MouseEvent e) {
        e.consume();
        threadSuspended = !threadSuspended;
        if (!threadSuspended)
            notify();
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mouseClicked(MouseEvent e) {
    }

    public String getAppletInfo() {
        return "Title: NervousText\nAuthor: Daniel Wyszynski\nDisplays a text banner that jitters.";
    }  
  
    public String[][] getParameterInfo() {
        String pinfo[][] = {
            {"text", "string", "Text to display"},
        };
        return pinfo;
    }
}
