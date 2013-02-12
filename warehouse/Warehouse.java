
package warehouse;

import java.io.InputStream;
import java.io.IOException;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.StringItem;
import javax.microedition.midlet.MIDlet;

/*
	A Sokoban clone for J2ME (c)2012 mumart@gmail.com
*/
public final class Warehouse extends MIDlet implements CommandListener {
	public static final String VERSION = "1.0 (c)2012 mumart@ gmail.com";

	private WarehouseCanvas warehouseCanvas;
	private Command gameCommand, helpCommand, backCommand, quitCommand;
	private List gameList;
	private Form helpForm;

	public Warehouse() throws Exception {
		String[] levels = readCatalog( "/warehouse/levels/levels.cat" );
		warehouseCanvas = new WarehouseCanvas( "/warehouse/levels/" + levels[ 0 ] + ".lev" );
		gameCommand = new Command( "Game", Command.SCREEN, 1 );
		helpCommand = new Command( "Help", Command.SCREEN, 1 );
		backCommand = new Command( "Back", Command.SCREEN, 1 );
		quitCommand = new Command( "Quit", Command.SCREEN, 1 );
		gameList = new List( "Select Game", List.IMPLICIT, levels, null );
		gameList.addCommand( backCommand );
		gameList.addCommand( quitCommand );
		gameList.setCommandListener( this );
		helpForm = new Form( "Warehouse", new Item[] {
			new StringItem( "About: ", "A Sokoban clone, " +
				"using level data from http://sokobano.de" ),
			new StringItem( "How To Play: ",
				"Push the yellow crates onto the red places using your directional keys. " +
				"Press '#' to reset the board or skip to the next level. " +
				"Press '*' to reset the board or skip forward 10 levels. Have fun!" ),
			new StringItem( "Version: ", VERSION )
		} );
		helpForm.addCommand( backCommand );
		helpForm.setCommandListener( this );
		warehouseCanvas.addCommand( gameCommand );
		warehouseCanvas.addCommand( helpCommand );
		warehouseCanvas.setCommandListener( this );
	}

	public void startApp() {
		Display display = Display.getDisplay( this );
		display.setCurrent( warehouseCanvas );
	}

	public void pauseApp() {
	}

	public void destroyApp( boolean unconditional ) {
	}

	public void commandAction( Command c, Displayable d ) {
		Display display = Display.getDisplay( this );
		try {
			if( c == gameCommand ) {
				display.setCurrent( gameList );
			} else if( c == List.SELECT_COMMAND && d == gameList ) {
				String name = gameList.getString( gameList.getSelectedIndex() );
				warehouseCanvas.loadGame( "/warehouse/levels/" + name + ".lev" );
				display.setCurrent( warehouseCanvas );
			} else if( c == helpCommand ) {
				display.setCurrent( helpForm );
			} else if( c == backCommand ) {
				display.setCurrent( warehouseCanvas );
			} else if( c == quitCommand ) {
				notifyDestroyed();
			}
		} catch( Exception e ) {
			display.setCurrent( new Alert( "Error", e.getMessage(), null, null ) );
		}
	}

	private String[] readCatalog( String resource ) throws IOException {
		// Load the resource.
		int len = 0;
		byte[] buf = new byte[ 1024 ];
		InputStream input = getClass().getResourceAsStream( resource );
		try {
			int count = 0;
			while( count >= 0 && len < buf.length ) {
				len += count;
				count = input.read( buf, len, buf.length - len );
			}
		} finally {
			input.close();
		}
		// Count the number of lines.
		int entries = 0;
		for( int idx = 0; idx < len; idx++ ) {
			if( buf[ idx ] == '\n' ) {
				entries++;
			}
		}
		// Initialise the output array.
		String[] catalog = new String[ entries ];
		int start = 0, entry = 0;
		for( int end = 0; end < len; end++ ) {
			if( buf[ end ] == '\n' ) {
				catalog[ entry++ ] = new String( buf, start, end - start, "ISO-8859-1" );
				start = end + 1;
			}
		}		
		return catalog;
	}
}

final class WarehouseCanvas extends Canvas {
	private final int
		TILE_FLOOR = 0,
		TILE_PLACE = 1, /* Bit 1 set to indicate place. */
		TILE_BRICK = 2,
		TILE_CRATE = 4,
		TILE_BLOKE = 6;

	private Image[] tileSet;
	private byte[] map = new byte[ 32 * 32 ];
	private byte[] levelData = new byte[ 16384 ];
	private int mapWidth, mapHeight, mapX, mapY, tileSize;
	private int levelDataIdx, levelDataLen, level, numMoves, blokeIdx;
	private Font statusFont = Font.getFont( Font.FACE_MONOSPACE, Font.STYLE_BOLD, Font.SIZE_SMALL );
	private String statusText;

	public WarehouseCanvas( String levelResource ) throws Exception {
		// Initialize tile images.
		tileSet = new Image[ 5 ];
		tileSet[ 0 ] = Image.createImage( "/warehouse/images/tiles6.png" );
		tileSet[ 1 ] = Image.createImage( "/warehouse/images/tiles8.png" );
		tileSet[ 2 ] = Image.createImage( "/warehouse/images/tiles12.png" );
		tileSet[ 3 ] = Image.createImage( "/warehouse/images/tiles16.png" );
		tileSet[ 4 ] = Image.createImage( "/warehouse/images/tiles24.png" );
		// Initialize level data.
		loadGame( levelResource );
	}

	public void loadGame( String resource ) throws IOException {
		InputStream input = getClass().getResourceAsStream( resource );
		try {
			int count = 0;
			levelDataLen = 0;
			while( count >= 0 && levelDataLen < levelData.length ) {
				levelDataLen += count;
				count = input.read( levelData, levelDataLen, levelData.length - levelDataLen );
			}
		} finally {
			input.close();
		}
		initLevel( 1 );
	}

	public void paint( Graphics g ) {
		int width = getWidth();
		int height = getHeight();
		int clipX = g.getClipX();
		int clipY = g.getClipY();
		int clipW = g.getClipWidth();
		int clipH = g.getClipHeight();
		int fontH = statusFont.getHeight();
		// Calculate the appropriate tile set.
		Image tiles = tileSet[ 0 ];
		tileSize = tiles.getHeight();
		for( int idx = 1; idx < tileSet.length; idx++ ) {
			int size = tileSet[ idx ].getHeight();
			if( width >= mapWidth * size
				&& height >= mapHeight * size + fontH + fontH ) {
				tiles = tileSet[ idx ];
				tileSize = size;
			}
		}
		// Calculate the position of the map.
		int mapW = mapWidth * tileSize;
		int mapH = mapHeight * tileSize;
		mapX = ( width - mapW ) >> 1;
		mapY = ( height - mapH ) >> 1;
		// Draw a black border.
		g.setColor( 0 );
		g.fillRect( 0, 0, width, mapY );
		g.fillRect( 0, mapY, mapX, mapH );
		g.fillRect( mapX + mapW, mapY, width - mapX - mapW, mapH );
		g.fillRect( 0, mapY + mapH, width, height - mapY - mapH );
		// Draw the status text.
		if( clipY < mapY ) {
			g.setColor( 0xFFCC00 );
			g.setFont( statusFont );
			g.drawString( statusText, mapX, mapY, Graphics.BOTTOM | Graphics.LEFT );
		}
		// Draw the map.
		int mapIdx = 0;
		for( int y = mapY, yEnd = mapY + mapH; y < yEnd; y += tileSize ) {
			// Don't bother drawing tiles outside the clip region.
			if( clipY < y + tileSize && clipY + clipH > y ) {
				for( int x = mapX, xEnd = mapX + mapW; x < xEnd; x += tileSize ) {
					if( clipX < x + tileSize && clipX + clipW > x ) {
						int tileIdx = map[ mapIdx ];
						g.setClip( x, y, tileSize, tileSize );
						g.drawImage( tiles, x - tileIdx * tileSize, y, Graphics.TOP | Graphics.LEFT );
						if( tileIdx == TILE_BRICK ) {
							// Draw a bevel around the wall.
							int tileX = mapIdx % mapWidth;
							int tileY = mapIdx / mapWidth;
							g.setColor( 0x0 );
							if( tileX + 1 >= mapWidth || map[ mapIdx + 1 ] != TILE_BRICK ) {
								g.fillRect( x + tileSize - 1, y, 1, tileSize );
							}
							if( tileY + 1 >= mapHeight || map[ mapIdx + mapWidth ] != TILE_BRICK ) {
								g.fillRect( x, y + tileSize - 1, tileSize, 1 );
							}
							g.setColor( 0xFFFFFF );
							if( tileX < 1 || map[ mapIdx - 1 ] != TILE_BRICK ) {
								g.fillRect( x, y, 1, tileSize );
							}
							if( tileY < 1 || map[ mapIdx - mapWidth ] != TILE_BRICK ) {
								g.fillRect( x, y, tileSize, 1 );
							}
						}
					}
					mapIdx++;
				}
			} else {
				mapIdx += mapWidth;
			}
		}
	}

	public void keyPressed( int key ) {
		switch( key ) {
			case KEY_STAR:
				if( numMoves < 1 ) { // Skip.
					initLevel( level + 10 );
				} else { // Reset.
					initLevel( level );
				}
				repaint();
				break;
			case KEY_POUND:
				if( numMoves < 1 ) {
					initLevel( level + 1 );
				} else {
					initLevel( level );
				}
				repaint();
				break;
			default:
				gameAction( getGameAction( key ) );
				break;
		}
	}

	public void keyRepeated( int key ) {
		gameAction( getGameAction( key ) );
	}

	public void sizeChanged( int w, int h ) {
		repaint();
	}

	private void gameAction( int action ) {
		// Decide where to move the bloke.
		int delta = 0;
		switch( action ) {
			case UP:    delta = -mapWidth; break;
			case DOWN:  delta =  mapWidth; break;
			case LEFT:  delta = -1; break;
			case RIGHT: delta =  1; break;
		}
		// Recalculate the map.
		switch( map[ blokeIdx + delta ] & -2 ) {
			case TILE_FLOOR:
				map[ blokeIdx ] = ( byte ) ( TILE_FLOOR + ( map[ blokeIdx ] & 1 ) );
				blokeIdx += delta;
				map[ blokeIdx ] = ( byte ) ( TILE_BLOKE + ( map[ blokeIdx ] & 1 ) );
				numMoves++;
				break;
			case TILE_CRATE:
				if( ( map[ blokeIdx + delta + delta ] & -2 ) == TILE_FLOOR ) {
					map[ blokeIdx ] = ( byte ) ( TILE_FLOOR + ( map[ blokeIdx ] & 1 ) );
					blokeIdx += delta;
					map[ blokeIdx ] = ( byte ) ( TILE_BLOKE + ( map[ blokeIdx ] & 1 ) );
					map[ blokeIdx + delta ] = ( byte ) ( TILE_CRATE + ( map[ blokeIdx + delta ] & 1 ) );
				}
				numMoves++;
				break;
		}
		// Decide whether the map is complete.
		boolean complete = true;
		for( int mapIdx = 0, mapEnd = mapWidth * mapHeight; mapIdx < mapEnd; mapIdx++ ) {
			if( map[ mapIdx ] == TILE_CRATE ) {
				complete = false;
				break;
			}
		}
		if( complete ) {
			initLevel( level + 1 );
			repaint();	
		} else {
			// Only repaint 9 tiles around the bloke.
			int blokeX = blokeIdx % mapWidth;
			int blokeY = blokeIdx / mapWidth;
			int clipX = mapX + ( blokeX - 1 ) * tileSize;
			int clipY = mapY + ( blokeY - 1 ) * tileSize;
			repaint( clipX, clipY, tileSize * 3, tileSize * 3 );
		}
	}

	private void initLevel( int level ) {
		// Find the specified level in the level data.
		int width = mapWidth;
		int height = mapHeight;
		byte[] data = levelData;
		int dataIdx = levelDataIdx;
		int currentLevel = this.level;
		if( level < currentLevel || currentLevel <= 1 ) {
			// Start search from the beginning.
			width = height = 0;
			currentLevel = 0;
			dataIdx = -1;
		}
		while( currentLevel < level ) {
			// Skip the current level.
			currentLevel++;
			dataIdx += ( width + 1 ) * height + 1;
			if( dataIdx >= levelDataLen ) {
				// Set the first level.
				dataIdx = 0;
				level = currentLevel = 1;
			}
			// Calculate the size of the map.
			width = 0;
			while( data[ dataIdx + width ] != '\n' ) {
				width++;
			}
			height = 1;
			while( data[ dataIdx + ( width + 1 ) * height ] != '\n' ) {
				height++;
			}
			mapWidth = width;
			mapHeight = height;
			levelDataIdx = dataIdx;
		}
		// Decode the level data into the map.
		int mapIdx = 0;
		for( int y = 0; y < height; y++ ) {
			for( int x = 0; x < width; x++ ) {
				// Convert ASCII to tile index.
				int tile = levelData[ dataIdx++ ] - '0';
				map[ mapIdx ] = ( byte ) tile;
				if( ( tile & -2 ) == TILE_BLOKE ) {
					// Found the bloke in the map.
					blokeIdx = mapIdx;
				}
				mapIdx++;
			}
			// Skip line feed.
			dataIdx++;
		}
		numMoves = 0;
		statusText = "Level " + level;
		this.level = level;
	}
}
