package downearth.gui
import Border._
import Background._
import downearth._

import org.lwjgl.opengl.Display

import simplex3d.math.Vec2i
import simplex3d.math.double._
import downearth.tools._
import downearth.generation.MaterialManager

object MainWidget extends Panel {
  val position = Vec2i(0)
  val size = Vec2i(Display.getWidth,Display.getHeight)

  border = NoBorder
  background = NoBackground

  override def setPosition(newPos:Vec2i, delay:Int) {}

  val dragStartPos = Vec2i(0)
  val startDir = Vec3(0)

  addReaction {
    case MouseClicked(pos) =>
      Player.primaryAction
    case DragStart(firstPos:Vec2i) =>
      dragStartPos := firstPos
      startDir := Player.dir
    case MouseDrag(mousePos0, mousePos1) =>

      val mouseDelta = Vec2(mousePos1 - mousePos0)
      mouseDelta *= 2.0 / size.y

      val deltaAngle = Vec3(mouseDelta.yx, 0)

      Player.rotate(deltaAngle)
  }

  override def safePosition(newPos:Vec2i) = {
    this.position
  }

  val drawCallLabel       = new Label( Vec2i(20,20), "<not set>" )
  val playerPositionLabel = new Label( Vec2i(20,40), "<not set>" )
  val inventoryButton = new Button(    Vec2i(20,60), "inventory")
  val keySettingsButton = new Button(  Vec2i(20,80), "key settings" ){
    override  def onClick() {
      boolSettingsWidget.visible =  false
      keySettingWidget.visible = !keySettingWidget.visible
    }
  }
  val boolSettingsButton = new Button( Vec2i(20,100), "bool settings" ){
    override  def onClick() {
      keySettingWidget.visible = false
      boolSettingsWidget.visible = !boolSettingsWidget.visible
    }
  }

  val inventory = new Inventory(Vec2i(20, 200), Vec2i(200,200)) {
    backGroundColor := Vec4(0.1,0.1,0.1,0.7)
    border = LineBorder

    val shovel = new ToolWidget( Shovel, position+Vec2i(40, 0) )
    val hammer = new ToolWidget( ConstructionTool, position+Vec2i(0 , 0) )

    children += hammer
    children += shovel
    assert(hammer.parent == this)
    assert(shovel.parent == this)

    children ++= MaterialManager.materials.zipWithIndex.map{
      case (material,i) => new MaterialWidget(material, position + Vec2i(i * 40, 40) )
    }

    children ++= Range(0, ConstructionTool.all.size).map(
      i => new ShapeWidget(i, position + Vec2i(i * 40, 80))
    )

    val superTool = new ToolWidget( TestBuildTool, position+Vec2i(80,0) )

    children += superTool

    selected = shovel

    listenTo(MainWidget)
    listenTo(inventoryButton)

    addReaction {
    case WidgetResized(MainWidget) =>
      val newPos = Vec2i(0)
      newPos.x = MainWidget.size.x - size.x - 20
      newPos.y = 20
      setPosition(newPos,0)
    case ButtonClicked(`inventoryButton`) =>
      visible = !visible
    }

    arrangeChildren()
  }

  val keySettingWidget = new KeySettingsWidget( Vec2i(20,120), Config )
  keySettingWidget.visible = false
  val boolSettingsWidget = new BoolSettingsWidget( Vec2i(20,120), Config )
  boolSettingsWidget.visible = false

  publish( WidgetResized(this) )

  children ++= Seq(
    inventory,
    inventoryButton,
    drawCallLabel,
    playerPositionLabel,
    keySettingWidget,
    keySettingsButton,
    boolSettingsWidget,
    boolSettingsButton
  )

}