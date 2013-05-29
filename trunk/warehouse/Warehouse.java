
package warehouse;

import java.io.InputStream;
import java.io.IOException;
import java.util.Random;

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
	A Sokoban clone for J2ME (c)2013 mumart@gmail.com
*/
public final class Warehouse extends MIDlet implements CommandListener {
	public static final String VERSION = "1.1 (c)2013 mumart@ gmail.com";

	private WarehouseCanvas warehouseCanvas;
	private Command backCommand, levelCommand, gameCommand, helpCommand, quitCommand;
	private List gameList, levelList;
	private Form helpForm;

	public Warehouse() throws Exception {
		String[] games = readCatalog( "/warehouse/levels/levels.cat" );
		backCommand = new Command( "Back", Command.BACK, 1 );
		levelCommand = new Command( "Level", Command.SCREEN, 1 );
		gameCommand = new Command( "Game", Command.SCREEN, 2 );
		helpCommand = new Command( "Help", Command.SCREEN, 3 );
		quitCommand = new Command( "Quit", Command.EXIT, 4 );
		gameList = new List( "Select Game", List.IMPLICIT, games, null );
		gameList.addCommand( backCommand );
		gameList.setCommandListener( this );
		levelList = new List( "Select Level", List.IMPLICIT );
		levelList.addCommand( backCommand );
		levelList.setCommandListener( this );
		warehouseCanvas = new WarehouseCanvas( "/warehouse/levels/" + games[ 0 ] + ".lev", levelList );	
		helpForm = new Form( "Warehouse", new Item[] {
			new StringItem( "About: ", "A Sokoban clone, " +
				"using level data from http://sokobano.de" ),
			new StringItem( "How To Play: ",
				"Push the yellow crates onto the red places using your directional keys. " +
				"Press '#' to reset the board or skip to the next level. " +
				"Press '*' to reset the board or skip to a random level. Have fun!" ),
			new StringItem( "Version: ", VERSION )
		} );
		helpForm.addCommand( backCommand );
		helpForm.setCommandListener( this );
		warehouseCanvas.addCommand( gameCommand );
		warehouseCanvas.addCommand( levelCommand );
		warehouseCanvas.addCommand( helpCommand );
		warehouseCanvas.addCommand( quitCommand );
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
			} else if( c == levelCommand ) {
				display.setCurrent( levelList );
			} else if( c == List.SELECT_COMMAND ) {
				if( d == gameList ) {
					levelList = new List( "Select Level", List.IMPLICIT );
					levelList.addCommand( backCommand );
					levelList.setCommandListener( this );
					String name = gameList.getString( gameList.getSelectedIndex() );
					warehouseCanvas.loadGame( "/warehouse/levels/" + name + ".lev", levelList );
				} else if( d == levelList ) {
					warehouseCanvas.setLevel( levelList.getSelectedIndex() );
				}
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
		byte[] buf = new byte[ 1024 ];
		int len = readResource( resource, buf );
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

	public static int readResource( String resource, byte[] buf ) throws IOException {
		InputStream input = resource.getClass().getResourceAsStream( resource );
		int len = 0;
		try {
			int count = 0;
			while( count >= 0 && len < buf.length ) {
				len += count;
				count = input.read( buf, len, buf.length - len );
			}
		} finally {
			input.close();
		}
		return len;
	}
}

final class WarehouseCanvas extends Canvas {
	private final int
		TILE_FLOOR = 0,
		TILE_PLACE = 1, /* Bit 1 set to indicate place. */
		TILE_BRICK = 2,
		TILE_CRATE = 4,
		TILE_BLOKE = 6;

	private List levelList;
	private Image[] tileSet;
	private byte[] map = new byte[ 32 * 32 ];
	private byte[] levelData = new byte[ 16384 ];
	private int[] levelOffsets = new int[ 256 ];
	private int mapWidth, mapHeight, mapX, mapY, tileSize;
	private int numLevels, levelIdx, numMoves, blokeIdx;
	private Font statusFont = Font.getFont( Font.FACE_MONOSPACE, Font.STYLE_BOLD, Font.SIZE_SMALL );
	private String statusText;
	private Random random = new Random();

	public WarehouseCanvas( String gameResource, List levelList ) throws Exception {
		// Initialize tile images.
		tileSet = new Image[ 5 ];
		tileSet[ 0 ] = Image.createImage( "/warehouse/images/tiles6.png" );
		tileSet[ 1 ] = Image.createImage( "/warehouse/images/tiles8.png" );
		tileSet[ 2 ] = Image.createImage( "/warehouse/images/tiles12.png" );
		tileSet[ 3 ] = Image.createImage( "/warehouse/images/tiles16.png" );
		tileSet[ 4 ] = Image.createImage( "/warehouse/images/tiles24.png" );
		// Initialize level data.
		loadGame( gameResource, levelList );
	}

	public void loadGame( String gameResource, List levelList ) throws IOException {
		this.levelList = levelList;
		numLevels = 0;
		int levelDataIdx = 0;
		int levelDataLen = Warehouse.readResource( gameResource, levelData );
		while( levelDataIdx < levelDataLen ) {
			// Determine the number of levels and the offset of each.
			levelOffsets[ numLevels++ ] = levelDataIdx;
			levelList.append( buildLevelName( numLevels, 0 ), null );
			int width = 0;
			while( levelData[ levelDataIdx + width ] != '\n' ) {
				width++;
			}
			int height = 1;
			while( levelData[ levelDataIdx + ( width + 1 ) * height ] != '\n' ) {
				height++;
			}
			levelDataIdx += ( width + 1 ) * height + 1;
		}
		setLevel( 0 );
	}

	public void setLevel( int level ) {
		levelIdx = level % numLevels;
		int levelDataIdx = levelOffsets[ levelIdx ];
		// Calculate the size of the map.
		mapWidth = 0;
		while( levelData[ levelDataIdx + mapWidth ] != '\n' ) {
			mapWidth++;
		}
		mapHeight = 1;
		while( levelData[ levelDataIdx + ( mapWidth + 1 ) * mapHeight ] != '\n' ) {
			mapHeight++;
		}
		// Decode the level data into the map.
		int mapIdx = 0;
		for( int y = 0; y < mapHeight; y++ ) {
			for( int x = 0; x < mapWidth; x++ ) {
				// Convert ASCII to tile index.
				int tile = levelData[ levelDataIdx++ ] - '0';
				map[ mapIdx ] = ( byte ) tile;
				if( ( tile & -2 ) == TILE_BLOKE ) {
					// Found the bloke in the map.
					blokeIdx = mapIdx;
				}
				mapIdx++;
			}
			// Skip line feed.
			levelDataIdx++;
		}
		numMoves = 0;
		statusText = "Level " + ( levelIdx + 1 );
		repaint();
		levelList.setSelectedIndex( levelIdx, true );
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
					setLevel( ( random.nextInt() & 0x7FFFFFFF ) % numLevels );
				} else { // Reset.
					setLevel( levelIdx );
				}
				break;
			case KEY_POUND:
				if( numMoves < 1 ) {
					setLevel( levelIdx + 1 );
				} else {
					setLevel( levelIdx );
				}
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
			// Change list text to indicate completed.
			levelList.set( levelIdx,  buildLevelName( levelIdx + 1, numMoves ), null );
			setLevel( levelIdx + 1 );
		} else {
			// Only repaint 9 tiles around the bloke.
			int blokeX = blokeIdx % mapWidth;
			int blokeY = blokeIdx / mapWidth;
			int clipX = mapX + ( blokeX - 1 ) * tileSize;
			int clipY = mapY + ( blokeY - 1 ) * tileSize;
			repaint( clipX, clipY, tileSize * 3, tileSize * 3 );
		}
	}
	
	private static String buildLevelName( int level, int moves ) {
		String levelName = String.valueOf( level );
		if( moves > 0 ) {
			levelName = levelName + " (" + moves + " moves)";
		}
		return levelName;
	}
}
