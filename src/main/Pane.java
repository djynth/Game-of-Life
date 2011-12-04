package main;

import io.Listener;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

/**
 * Represents the majority of the GUI, the small rounded-rectangular box containing buttons and diagnostics.
 * This pane can be dragged around by the image of dots in the upper-right corner, making the pane temporarily transparent.
 * The arrow image on the left allows access to the selector, which gives the ability to pick one of the preset patterns.
 * The arrow with the vertical line skips to the next generation, while the play/pause button pauses or unpauses the simulation.
 * The broom clears the entire simulation board and resets the generation to 0.
 * Lastly, the speed slider allows the user to speed up or slow down the simulation.
 * 
 * @author Dominic
 */
public class Pane implements Runnable
{	
	public boolean paused = true;
	private boolean beingDragged = false;
	private BufferedImage img = null;
	
	public static Color lightBlue = new Color(0, 163, 231);
	
	private float[] scales = {1f, 1f, 1f, 1f};
	private float[] offsets = new float[4];
	private float playAlpha = 0f;
	private float pauseAlpha = 1f;
	private float fadeSpeed = 0.033f;
	private float paneAlpha = 1f;
	private float paneFadeSpeed = 0.025f;
	private float paneAlphaMin = 0.5f;
	public Font fontBold = new Font(Font.SANS_SERIF, Font.BOLD, 18);
	public Font fontPlain = new Font(Font.SANS_SERIF, Font.PLAIN, 18);
	
	private Information info;
	public int x;
	public int y;
	public int width;
	public int height;
	private int paneImageIndex;
	private int dotsImageIndex;
	private int nextImageIndex;
	private int broomImageIndex;
	private int playImageIndex;
	private int pauseImageIndex;
	
	private long period = 10;
	
	public PatternSelection selector;
	private Point dragOrigin = new Point();
	
	public Rectangle bounds = new Rectangle();
	private Rectangle playBounds = new Rectangle();
	private Rectangle dotsBounds = new Rectangle();
	private Rectangle nextBounds = new Rectangle();
	private Rectangle broomBounds = new Rectangle();
	private RescaleOp rescaler;
	public RollOver playRO;
	public RollOver nextRO;
	public RollOver clearRO;
	public RollOver dotsRO;
	
	public SpeedBar speedBar;
	
	public Thread playROThread;
	public Thread nextROThread;
	public Thread clearROThread;
	public Thread dotsROThread;
	public Thread selectorThread;
	
	/**
	 * Creates a new Pane object with the given Information.
	 * All required images are loaded with the Information's ImageLoader, even though it is unlikely that any other class will use them.
	 * The width and height are found and bounding rectangles are created or adjusted.
	 * RollOvers are initialized and run in their individual threads.
	 * 
	 * @param info - the current Information
	 */
	public Pane(Information info)
	{
		this.info = info;
		info.pane = this;
		
		info.listener.requestNotification(this, "mousePressed", Listener.TYPE_MOUSE_PRESSED, Listener.CODE_BUTTON1);
		info.listener.requestNotification(this, "mouseReleased", Listener.TYPE_MOUSE_RELEASED, Listener.CODE_BUTTON1);
		info.listener.requestNotification(this, "mouseDragged", Listener.TYPE_MOUSE_DRAGGED, Listener.CODE_BUTTON1);
		paneImageIndex = info.imageLoader.add("images/pane.png", "pane", Transparency.TRANSLUCENT);
		dotsImageIndex = info.imageLoader.add("images/dots.png", "dots", Transparency.TRANSLUCENT);
		nextImageIndex = info.imageLoader.add("images/next.png", "next", Transparency.TRANSLUCENT);
		broomImageIndex = info.imageLoader.add("images/broom.png", "broom", Transparency.TRANSLUCENT);
		playImageIndex = info.imageLoader.add("images/play.png", "play", Transparency.TRANSLUCENT);
		pauseImageIndex = info.imageLoader.add("images/pause.png", "pause", Transparency.TRANSLUCENT);
		
		width = info.imageLoader.get(paneImageIndex).getWidth();
		height = info.imageLoader.get(paneImageIndex).getHeight();
		x = info.screen.width - width - info.screen.width/20;
		y = info.screen.height - height - info.screen.height/30;
		
		img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		speedBar = new SpeedBar(info);
		selector = new PatternSelection(info);
		
		playRO = new RollOver(new Rectangle(), info);
		nextRO = new RollOver(new Rectangle(), info);
		clearRO = new RollOver(new Rectangle(), info);
		dotsRO = new RollOver(new Rectangle(), info);
		
		setRectangles();
		
		playROThread = new Thread(playRO);
		nextROThread = new Thread(nextRO);
		clearROThread = new Thread(clearRO);
		dotsROThread = new Thread(dotsRO);
		selectorThread = new Thread(selector);
		playROThread.start();
		nextROThread.start();
		clearROThread.start();
		dotsROThread.start();
		selectorThread.start();
	}
	
	/**
	 * Creates or adjusts all of the bounding rectangles with the current x and y coordinates.
	 */
	public void setRectangles()
	{
		bounds = new Rectangle(x, y, width, height);
		
		playBounds = new Rectangle(
				x + width/4 - info.imageLoader.get(playImageIndex).getWidth()/2, 
				y + height/2 - info.imageLoader.get(playImageIndex).getHeight()/2, 
				info.imageLoader.get(playImageIndex).getWidth(),
				info.imageLoader.get(playImageIndex).getHeight());
		
		dotsBounds = new Rectangle(
				x + 17*width/20 - info.imageLoader.get(dotsImageIndex).getWidth()/2,
				y + 7*height/24 - info.imageLoader.get(dotsImageIndex).getHeight()/2,
				info.imageLoader.get(dotsImageIndex).getWidth(),
				info.imageLoader.get(dotsImageIndex).getHeight());
		
		nextBounds = new Rectangle(playBounds.x + playBounds.width + 10,
				playBounds.y,
				info.imageLoader.get(nextImageIndex).getBufferedImage().getWidth(),
				info.imageLoader.get(nextImageIndex).getBufferedImage().getHeight());
		
		broomBounds = new Rectangle(nextBounds.x + nextBounds.width + 10,
				nextBounds.y,
				info.imageLoader.get(broomImageIndex).getBufferedImage().getWidth(),
				info.imageLoader.get(broomImageIndex).getBufferedImage().getHeight());
		
		speedBar.bounds = new Rectangle(x + 280, y + 45, 100, 40);
		
		playRO.bounds = playBounds;
		nextRO.bounds = nextBounds;
		clearRO.bounds = broomBounds;
		dotsRO.bounds = dotsBounds;
		
		speedBar.setSliderBounds(speedBar.bounds.x + speedBar.sliderX, true);
		selector.setBounds();
	}
	
	/**
	 * Runs the Pane in its own Thread by continually updating and sleeping.
	 */
	public void run() 
	{
		while (true)
		{
			update();
			try
			{
				Thread.sleep(period);
			}
			catch (InterruptedException e) { }
		}
	}
	
	/**
	 * Updates the Pane by adjusting alpha values for the play/pause images and the pane itself.
	 * The play and pause images fade in and out when the play/pause button is pressed.
	 * The pane fades out when the dots are pressed and held and then fades in when they are released.
	 */
	public void update()
	{
		if (paused)
		{
			pauseAlpha = Math.min(pauseAlpha + fadeSpeed, 1f);
			playAlpha = Math.max(playAlpha - fadeSpeed, 0f);
		}
		else
		{
			playAlpha = Math.min(playAlpha + fadeSpeed, 1f);
			pauseAlpha = Math.max(pauseAlpha - fadeSpeed, 0f);
		}
		if (beingDragged)
		{
			paneAlpha = Math.max(paneAlpha - paneFadeSpeed, paneAlphaMin);
		}
		else
		{
			paneAlpha = Math.min(paneAlpha + paneFadeSpeed, 1f);
		}
	}
	
	/**
	 * Called by the Listener when the left mouse button (BUTTON1) has been pressed.
	 * First, the event is checked to see if it should be consumed by the OperationBar, which has higher precedence than the Pane.
	 * If it has not been consumed, various button bounds are checked to see if the event is within them and, if so, an appropriate action is taken.
	 * 
	 * @param e - the MouseEvent that triggered this call
	 */
	public void mousePressed(MouseEvent e)
	{
		if (!info.opBar.consumed(e))
		{
			if (playBounds.contains(e.getX(), e.getY()))
			{
				paused = !paused;
			}
			else if (nextBounds.contains(e.getX(), e.getY()))
			{
				info.map.update();
			}
			else if (broomBounds.contains(e.getX(), e.getY()))
			{
				info.map.clear();
			}
			else if (dotsBounds.contains(e.getX(), e.getY()))
			{
				beingDragged = true;
				dragOrigin = new Point(e.getX() - x, e.getY() - y);
			}
		}
	}
	
	/**
	 * Called by the Listener when the left mouse button (BUTTON1) has been released.
	 * Simply sets the beingDragged flag to false, making sure that the pane does not continue to follow the mouse.
	 * 
	 * @param e - the MouseEvent that triggered this call
	 */
	public void mouseReleased(MouseEvent e)
	{
		beingDragged = false;
	}
	
	public void mouseDragged(MouseEvent e)
	{
		if (beingDragged)
		{
			x = e.getX() - dragOrigin.x;
			y = e.getY() - dragOrigin.y;
			if (x < 0)
			{
				x = 0;
			}
			if (y < 0)
			{
				y = 0;
			}
			if (x + width > info.screen.width)
			{
				x = info.screen.width - width;
			}
			if (y + height > info.screen.height)
			{
				y = info.screen.height - height;
			}
			setRectangles();
		}
	}
	
	/**
	 * Determines whether the given MouseEvent should be consumed by the Pane or whether it can continue for further processing.
	 * 
	 * @param e - the MouseEvent that has been generated
	 * @return consumed - true if the MouseEvent occurred within the boundaries of the Pane, false otherwise
	 */
	public boolean consumed(MouseEvent e)
	{
		if (bounds.contains(e.getPoint()))
		{
			return true;
		}
		return false;
	}
	
	/**
	 * Draws the pane on the given Graphics at the proper place and with the current alpha value.
	 * First, the image is refreshed with all the subimages drawn.
	 * This image is drawn to a BufferedImage which is in turn drawn to the given Graphics, with the current alpha values.
	 * 
	 * @param g - the current Graphics context
	 */
	public void draw(Graphics2D g)
	{
		drawToImage();
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		bi.getGraphics().drawImage(img, 0, 0, width, height, null);
		scales[0] = scales[1] = scales[2] = scales[3] = paneAlpha;
		rescaler = new RescaleOp(scales, offsets, null);
		g.drawImage(bi, rescaler, x, y);
	}
	
	/**
	 * Draws all of the parts of the Pane to the BufferedImage img.
	 * This BufferedImage is then used to draw, with the current transparency, the entire pane to the screen.
	 * First, the selector and the RollOvers are drawn, followed by the dots, next image, and broom.
	 * The play and pause buttons are drawn with their own respective alpha value, if it is greater than 0.
	 * Finally, the text that displays the current generation is drawn.
	 */
	private void drawToImage()
	{
		img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = (Graphics2D)img.getGraphics();
		info.imageLoader.get(paneImageIndex).draw(0, 0, g);
		selector.drawToImage(g);
		playRO.draw(-x, -y, g);
		nextRO.draw(-x, -y, g);
		clearRO.draw(-x, -y, g);
		dotsRO.draw(-x, -y, g);
		info.imageLoader.get(dotsImageIndex).draw(dotsBounds.x - x, dotsBounds.y - y, g);
		info.imageLoader.get(nextImageIndex).draw(nextBounds.x - x, nextBounds.y - y, g);
		info.imageLoader.get(broomImageIndex).draw(broomBounds.x - x, broomBounds.y - y, g);
		
		if (pauseAlpha > 0)
		{
			BufferedImage bi = new BufferedImage(
					info.imageLoader.get(pauseImageIndex).getWidth(),
					info.imageLoader.get(pauseImageIndex).getHeight(),
					BufferedImage.TYPE_INT_ARGB);
			Graphics pauseG = bi.getGraphics();
			info.imageLoader.get(pauseImageIndex).draw(0, 0, (Graphics2D)pauseG);
			scales[3] = pauseAlpha;
			rescaler = new RescaleOp(scales, offsets, null);
			g.drawImage(bi, rescaler, playBounds.x - x, playBounds.y - y);
		}
		
		if (playAlpha > 0)
		{
			BufferedImage bi = new BufferedImage(
					info.imageLoader.get(playImageIndex).getWidth(),
					info.imageLoader.get(playImageIndex).getHeight(),
					BufferedImage.TYPE_INT_ARGB);
			Graphics playG = bi.getGraphics();
			info.imageLoader.get(playImageIndex).draw(0, 0, (Graphics2D)playG);
			scales[3] = playAlpha;
			rescaler = new RescaleOp(scales, offsets, null);
			g.drawImage(bi, rescaler, playBounds.x - x,  playBounds.y - y);
		}
		
		g.setColor(Color.black);
		g.setFont(fontBold);
		g.drawString(new Integer(info.generation).toString(), 281, 36);
		g.setColor(lightBlue);
		g.drawString(new Integer(info.generation).toString(), 280, 35);
		
		speedBar.drawToImage(g);
	}
}