
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
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/*
	A Sokoban clone for J2ME (c)2013 mumart@gmail.com
*/
public final class Warehouse extends MIDlet implements CommandListener {
	public static final String VERSION = "1.5 (c)2013 mumart@ gmail.com";

	private WarehouseCanvas warehouseCanvas;
	private Command okCommand, backCommand, quitCommand, undoCommand;
	private Command gameCommand, levelCommand, resetCommand, nextCommand;
	private Command randomCommand, clearCommand, helpCommand;
	private Form helpForm, levelForm;
	private TextField levelField;
	private List gameList;

	public Warehouse() throws Exception {
		String[] games = readCatalog( "/warehouse/levels/levels.cat" );
		okCommand = new Command( "OK", Command.OK, 1 );
		backCommand = new Command( "Back", Command.BACK, 1 );
		quitCommand = new Command( "Quit", Command.EXIT, 1 );
		undoCommand = new Command( "Undo", Command.SCREEN, 1 );
		gameCommand = new Command( "Select Game", Command.SCREEN, 2 );
		levelCommand = new Command( "Select Level", Command.SCREEN, 3 );
		resetCommand = new Command( "Reset Level", Command.SCREEN, 4 );
		nextCommand = new Command( "Next Level (#)", Command.SCREEN, 5 );
		randomCommand = new Command( "Random Level (*)", Command.SCREEN, 6 );
		clearCommand = new Command( "Clear Game Data", Command.SCREEN, 7 );
		helpCommand = new Command( "Help", Command.SCREEN, 8 );
		gameList = new List( "Select Game", List.IMPLICIT, games, null );
		gameList.addCommand( backCommand );
		gameList.setCommandListener( this );
		warehouseCanvas = new WarehouseCanvas( "/warehouse/levels/" + games[ 0 ] + ".lev" );	
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
		levelField = new TextField( "Level", "1", 3, TextField.NUMERIC );
		levelForm = new Form( "Select Level", new Item[] { levelField } );
		levelForm.addCommand( okCommand );
		levelForm.addCommand( backCommand );
		levelForm.setCommandListener( this );
		warehouseCanvas.addCommand( quitCommand );
		warehouseCanvas.addCommand( undoCommand );
		warehouseCanvas.addCommand( gameCommand );
		warehouseCanvas.addCommand( levelCommand );
		warehouseCanvas.addCommand( resetCommand );
		warehouseCanvas.addCommand( nextCommand );
		warehouseCanvas.addCommand( randomCommand );
		warehouseCanvas.addCommand( clearCommand );
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
			if( c == quitCommand ) {
				warehouseCanvas.saveGameData();
				notifyDestroyed();
			} else if( c == undoCommand ) {
				warehouseCanvas.undoMove();
			} else if( c == gameCommand ) {
				display.setCurrent( gameList );
			} else if( c == List.SELECT_COMMAND && d == gameList ) {
				String name = gameList.getString( gameList.getSelectedIndex() );
				warehouseCanvas.loadGame( "/warehouse/levels/" + name + ".lev" );
				display.setCurrent( warehouseCanvas );
			} else if( c == levelCommand ) {
				levelField.setString( String.valueOf( warehouseCanvas.getLevel() ) );
				display.setCurrent( levelForm );
			} else if( c == resetCommand ) {
				warehouseCanvas.setLevel( warehouseCanvas.getLevel() );
				display.setCurrent( warehouseCanvas );
			} else if( c == nextCommand ) {
				warehouseCanvas.setLevel( warehouseCanvas.getLevel() + 1 );
				display.setCurrent( warehouseCanvas );
			} else if( c == randomCommand ) {
				warehouseCanvas.randomLevel();
				display.setCurrent( warehouseCanvas );
			} else if( c == clearCommand ) {
				warehouseCanvas.clearGameData();
				warehouseCanvas.setLevel( warehouseCanvas.getLevel() );
				display.setCurrent( warehouseCanvas );
			} else if( c == helpCommand ) {
				display.setCurrent( helpForm );
			} else if( c == okCommand && d == levelForm ) {
				int level = 1;
				try {
					level = Integer.parseInt( levelField.getString() );
				} catch( NumberFormatException e ) {
				}
				warehouseCanvas.setLevel( level );
				display.setCurrent( warehouseCanvas );
			} else if( c == backCommand ) {
				display.setCurrent( warehouseCanvas );
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

	private Image[] tileSet;
	private byte[] map = new byte[ 32 * 32 ];
	private byte[] levelData = new byte[ 16384 ];
	private short[] levelOffsets = new short[ 256 ];
	private byte[] gameRecord = new byte[ 256 * 2 ];
	private byte[] undoBuffer = new byte[ 256 ];
	private int mapWidth, mapHeight, mapX, mapY, tileSize;
	private int numLevels, levelIdx, numMoves, blokeIdx, undoIdx;
	private boolean gameRecordChanged;
	private Font statusFont = Font.getFont( Font.FACE_MONOSPACE, Font.STYLE_BOLD, Font.SIZE_SMALL );
	private Random random = new Random();
	private RecordStore recordStore;
	private String statusText;

	public WarehouseCanvas( String gameResource ) throws Exception {
		// Initialize tile images.
		tileSet = new Image[ 5 ];
		tileSet[ 0 ] = Image.createImage( "/warehouse/images/tiles6.png" );
		tileSet[ 1 ] = Image.createImage( "/warehouse/images/tiles8.png" );
		tileSet[ 2 ] = Image.createImage( "/warehouse/images/tiles12.png" );
		tileSet[ 3 ] = Image.createImage( "/warehouse/images/tiles16.png" );
		tileSet[ 4 ] = Image.createImage( "/warehouse/images/tiles24.png" );
		// Initialize level data.
		loadGame( gameResource );
	}

	public void saveGameData() {
		if( recordStore != null && gameRecordChanged ) {
			try {
				// Store any changes to the current game.
				recordStore.setRecord( 1, gameRecord, 0, numLevels * 2 );
				gameRecordChanged = false;
			} catch( RecordStoreException e ) {
				// Ignore errors.
			}
		}
	}

	public void clearGameData() {
		for( int idx = 0, end = numLevels * 2; idx < end; idx++ ) {
			gameRecord[ idx ] = 0;
		}
		gameRecordChanged = true;
	}

	public void loadGame( String gameResource ) throws IOException {
		saveGameData();
		numLevels = 0;
		int levelDataIdx = 0;
		int levelDataLen = Warehouse.readResource( gameResource, levelData );
		while( levelDataIdx < levelDataLen ) {
			// Determine the number of levels and the offset of each.
			levelOffsets[ numLevels++ ] = ( short ) levelDataIdx;
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
		try {
			// Initialize the record array.
			for( int idx = 0, end = numLevels * 2; idx < end; idx++ ) {
				gameRecord[ idx ] = 0;
			}
			// Load the record for this game from the record store.
			String recordStoreName = gameResource;
			if( recordStoreName.length() > 32 ) {
				// Use the first 32 characters of the resource path for the name.
				recordStoreName = recordStoreName.substring( 0, 32 );
			}
			recordStore = RecordStore.openRecordStore( recordStoreName, true );
			if( recordStore.getNumRecords() < 1 ) {
				// Create new record.
				recordStore.addRecord( gameRecord, 0, numLevels * 2 );
			} else {
				// Retrieve saved record.
				recordStore.getRecord( 1, gameRecord, 0 );
			}
		} catch( RecordStoreException e ) {
			// Ignore errors.
		}
		setLevel( 1 );
	}

	public int getLevel() {
		return levelIdx + 1;
	}
	
	public void randomLevel() {
		int rnd = random.nextInt() & 0x7FFFFFFF;
		setLevel( ( rnd % numLevels ) + 1 );
	}

	public void setLevel( int level ) {
		levelIdx = ( level - 1 ) % numLevels;
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
		// Update status text.
		statusText = "Level " + ( levelIdx + 1 );
		int recordMoves = getRecordMoves();
		if( recordMoves > 0 ) {
			statusText += " (" + recordMoves + " moves)";
		}
		// Clear undo history.
		undoIdx = 0;
		undoBuffer[ undoIdx ] = 0;
		numMoves = 0;
		repaint();
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
			int textX = ( width - statusFont.stringWidth( statusText ) ) >> 1;
			if( textX < 0 ) {
				textX = 0;
			}
			g.setColor( 0xFFCC00 );
			g.setFont( statusFont );
			g.drawString( statusText, textX, mapY, Graphics.BOTTOM | Graphics.LEFT );
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
				if( numMoves < 1 ) {
					randomLevel();
				} else { // Reset.
					setLevel( getLevel() );
				}
				break;
			case KEY_POUND:
				if( numMoves < 1 ) { // Skip.
					setLevel( getLevel() + 1 );
				} else {
					setLevel( getLevel() );
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

	public void undoMove() {
		int move = undoBuffer[ undoIdx & 0xFF ];
		int delta = move >> 1;
		if( delta != 0 ) {
			if( ( move & 1 ) == 0 ) {
				map[ blokeIdx ] = ( byte ) ( TILE_FLOOR + ( map[ blokeIdx ] & 1 ) );
			} else {
				// Crate pushed.
				map[ blokeIdx ] = ( byte ) ( TILE_CRATE + ( map[ blokeIdx ] & 1 ) );
				map[ blokeIdx + delta ] = ( byte ) ( TILE_FLOOR + ( map[ blokeIdx + delta ] & 1 ) );
			}				
			blokeIdx -= delta;
			map[ blokeIdx ] = ( byte ) ( TILE_BLOKE + ( map[ blokeIdx ] & 1 ) );
			undoIdx--;
			numMoves--;
			repaint();
		}
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
				undoBuffer[ ( ++undoIdx ) & 0xFF ] = ( byte ) ( delta << 1 );
				undoBuffer[ ( undoIdx + 1 ) & 0xFF ] = 0;
				numMoves++;
				break;
			case TILE_CRATE:
				if( ( map[ blokeIdx + delta + delta ] & -2 ) == TILE_FLOOR ) {
					map[ blokeIdx ] = ( byte ) ( TILE_FLOOR + ( map[ blokeIdx ] & 1 ) );
					blokeIdx += delta;
					map[ blokeIdx ] = ( byte ) ( TILE_BLOKE + ( map[ blokeIdx ] & 1 ) );
					map[ blokeIdx + delta ] = ( byte ) ( TILE_CRATE + ( map[ blokeIdx + delta ] & 1 ) );
					undoBuffer[ ( ++undoIdx ) & 0xFF ] = ( byte ) ( ( delta << 1 ) + 1 );
					undoBuffer[ ( undoIdx + 1 ) & 0xFF ] = 0;
					numMoves++;
				}
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
			// Update the number of moves for this level.
			int recordMoves = getRecordMoves();
			if( numMoves < recordMoves || recordMoves < 1 ) {
				setRecordMoves( numMoves );
			}
			// Next level.
			setLevel( getLevel() + 1 );
		} else {
			// Only repaint 9 tiles around the bloke.
			int blokeX = blokeIdx % mapWidth;
			int blokeY = blokeIdx / mapWidth;
			int clipX = mapX + ( blokeX - 1 ) * tileSize;
			int clipY = mapY + ( blokeY - 1 ) * tileSize;
			repaint( clipX, clipY, tileSize * 3, tileSize * 3 );
		}
	}
	
	private int getRecordMoves() {
		// Get the number of moves stored for the current level.
		int recIdx = levelIdx << 1;
		return ( ( gameRecord[ recIdx ] & 0xFF ) << 8 ) | ( gameRecord[ recIdx + 1 ] & 0xFF );
	}
	
	private void setRecordMoves( int numMoves ) {
		int recIdx = levelIdx << 1;
		gameRecord[ recIdx ] = ( byte ) ( numMoves >> 8 );
		gameRecord[ recIdx + 1 ] = ( byte ) numMoves;
		gameRecordChanged = true;
	}
}
