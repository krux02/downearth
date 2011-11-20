package openworld.gui

import simplex3d.math._
import simplex3d.math.float._
import simplex3d.math.float.functions._

import org.lwjgl.opengl.GL11._
import org.newdawn.slick.opengl.Texture
import org.lwjgl.input.Mouse

import openworld.Config._
import openworld.Util._
import openworld._

// die GUI wird sebst als Kamera implementiert weil sie ihre eigene 2D Szene hat
object GUI extends Camera {
	
	val inventory = new Inventory(Vec2i(20, 200), Vec2i(200,200)) {
		children += new Hammer(position+Vec2i(0 , 0))
		children += new Shovel(position+Vec2i(40, 0))
		children ++= Range(0,4).map(
			i => new MaterialWidget(i, position + Vec2i(i * 40, 40) )
		)
		
		//children += new Label(position, "Test")
		
		arrangeChildren
		setTopRight
		
		var moved = false
		def setTopRight = setPosition(Vec2i(screenWidth - size.x - 20, 20))
		override def dragStop(mousePos:Vec2i) { moved = true }
	}
	
	MainWidget.children += inventory
	
		
	def applyortho {
		glDisable(GL_DEPTH_TEST)
		glDisable(GL_LIGHTING)
		
		glMatrixMode(GL_PROJECTION)
		glLoadIdentity
		glOrtho(0, screenWidth, screenHeight, 0, -100, 100)
		
		glMatrixMode(GL_MODELVIEW)
		glLoadIdentity
	}
	
	def renderScene {
		glPolygonMode( GL_FRONT_AND_BACK, GL_FILL ) // no wireframes
		applyortho
		
		Draw.addText("%d fps" format Main.currentfps)
		Draw.addText("drawcalls: " + World.drawcalls +
			", empty: " + World.emptydrawcalls + "")
		Draw.addText("frustum culled nodes: " + World.frustumculls)
		Draw.addText("")
		Draw.addText("Inventory: " + Player.inventory.materials)
		
		if( !Player.isGhost ) {
			Draw.addText("Player Position: " + round10(Player.position) )
			Draw.addText("Player Velocity: " + round10(Player.velocity) )
		}
		
		glDisable( GL_LIGHTING )
		glDisable( GL_TEXTURE_2D )
		glEnable(GL_BLEND)
		glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA)
		
		Draw.drawTexts
		DisplayEventManager.draw

		if( Mouse.isGrabbed )
			Draw.crossHair
		
		MainWidget.invokeDraw
		
		glDisable(GL_BLEND)
	}
}


class MaterialWidget(val matId:Int, _pos:Vec2i)
	extends InventoryItem(_pos, TextureManager.materials, Vec2(matId/4f,0), Vec2(0.25f,1) ) {
	
	override def mouseClicked(mousePos:Vec2i) {
		super.mouseClicked(mousePos)
		ConstructionTool.selectedMaterial = matId
		DisplayEventManager.showEventText("Material " + matId)
	}
	
	override def draw {
		super.draw

		val text = floor(Player.inventory.materials(matId).toFloat).toInt
		val textSize = Vec2i(ConsoleFont.font.getWidth(text.toString) + 2, ConsoleFont.height)
		val textPos = position + size - textSize
		import org.newdawn.slick.Color.white
		Draw.drawString(textPos, text, white)
	}
}

class ToolWidget(val tool:PlayerTool, _pos:Vec2i, _texPosition:Vec2, _texSize:Vec2)
	extends InventoryItem(_pos, TextureManager.tools, _texPosition, _texSize) {
	
	override def mouseClicked(mousePos:Vec2i) {
		super.mouseClicked(mousePos)
		Player.selectTool(tool)
		DisplayEventManager.showEventText("Tool " + tool)
	}
}

//TODO: class ShapeWidget(val shape:Polyeder, _pos:Vec2i) extends InventoryItem(_pos)


class Hammer(_pos:Vec2i) extends ToolWidget( ConstructionTool, _pos, Vec2(0),      Vec2(0.5f) )
class Shovel(_pos:Vec2i) extends ToolWidget( Shovel, _pos, Vec2(0.5f,0), Vec2(0.5f) )


class InventoryItem(_pos:Vec2i, texture:Texture, _texPosition:Vec2, _texSize:Vec2)
	extends TextureWidget(_pos, Vec2i(32), texture, _texPosition, _texSize )
	with Draggable {
	
	var selected = false
	def select { selected = true; border = new LineBorder(Vec4(0.2f,0.4f,1f,1)) }
	def deselect { selected = false; border = new LineBorder(Vec4(1,1,1,1)) }
	
	override def dragStop(mousePos:Vec2i) = parent.arrangeChildren
	
	override def mouseClicked(mousePos:Vec2i) {
		if( selected )
			deselect
		else
			select
	}
}

class Inventory(_pos:Vec2i, _size:Vec2i) extends GridPanel(_pos, _size, 40) with Draggable {
/* TODO
	def deselectOfType[T] {
		for( child <- children; if( child.isInstanceOf[T] ) )
			child.deselect
	}*/

}









