package downearth.rendering

import javax.imageio.ImageIO
import java.awt.image.DataBufferByte
import org.lwjgl.BufferUtils
import simplex3d.math.double._
import java.nio.ByteBuffer
import simplex3d.math.{ConstVec2i, ReadVec2i}

/**
 * User: arne
 * Date: 29.04.13
 * Time: 21:56
 */

object Surface{
  def concatHorizontal(images:Seq[Surface]):Surface = {
    val height = images.head.height
    require( (true /: images) ( _ && _.height == height ) )
    val width = (0 /: images) ( _ + _.width )

    val data = new Array[Int](width*height)

    var offset = 0
    for(line <- 0 until height) {
      for(img <- images) {
        Array.copy(img,line*img.width, data, offset, img.width)
        offset += img.width
      }
    }

    new Surface(width, height, data)
  }

  def concatVertical(images:Seq[Surface]):Surface = {
    val width = images.head.width
    require( (true /: images) ( _ && _.height == width ) )
    val height = (0 /: images) ( _ + _.height )

    val data = Array.concat( images.map(_.data):_* )

    new Surface(width, height, data)
  }

  def blit(img:Surface, imgrect:Rect, screen:Surface, dstrect:Rect):Unit = ???
}

class Rect(var x:Int, var y:Int, var w:Int, var h:Int) {
  def this(pos:ReadVec2i, size:ReadVec2i) = this(pos.x, pos.y, size.x, size.y)

  def pos:ReadVec2i = ConstVec2i(x,y)
  def pos_=(v:ReadVec2i) {
    x = v.x
    y = v.y
  }

  def size:ReadVec2i = ConstVec2i(w,h)
  def size_=(v:ReadVec2i) {
    w = v.x
    y = v.y
  }

}

class Surface(val width:Int, val height:Int, val data:Array[Int] ) {
  require( ((width - 1) & width) == 0 )
  require( ((height - 1) & height) == 0 )
  require( data.length == width * height, "data length is %d , but should be %d, width: %d, height: %d".format(data.length,width*height,width,height) )
}

class TextureLoader {

  private def createBuffer( pixels:Array[Byte], hasAlphaChannel:Boolean ) = {
    val buffer = BufferUtils.createByteBuffer(pixels.length)
    if( hasAlphaChannel ) {
      // the default pixel format has its alpha channel at the wrong position for OpenGl Loading. So we need to convert the pixels
      for( Array(a,b,g,r) <- pixels.grouped(4) ) {
        buffer put b
        buffer put g
        buffer put r
        buffer put a
      }
    }
    else {
      buffer.put(pixels)
    }
    buffer.rewind()
    buffer
  }

  private def makeIntArray( pixels:Array[Byte], hasAlphaChannel:Boolean ) = {
    val bpp = if( hasAlphaChannel ) 4 else 3
    val array = new Array[Int](pixels.length / bpp)
    if( hasAlphaChannel ) {
      var i = 0
      for( Array(a,b,g,r) <- pixels.grouped(4) ) {
        array(i) =
        (b & 0xff) << 0 |
        (g & 0xff) << 8  |
        (r & 0xff) << 16 |
        (a & 0xff) << 24
        i += 1
      }
    }
    else {
      var i = 0
      for( Array(b,g,r) <- pixels.grouped(3) ) {
        array(i) =
          (b & 0xff) << 0 |
            (g & 0xff) << 8  |
            (r & 0xff) << 16 |
            (0xff) << 24
        i += 1
      }
    }
    array
  }

  case class QuadTexCoords(v1:Vec2, v2:Vec2, v3:Vec2, v4:Vec2)


  def createTextureAtlas( names:Seq[String] ) : Surface = {

    var maxWidth = 0
    var maxHeight = 0

    val images =
    for(name <- names) yield {
      val image = readImage(name)
      val width = image.getWidth
      val height = image.getHeight

      if(width > maxWidth)
        maxWidth = width
      if(height > maxHeight)
        maxHeight = height

      val hasAlphaChannel = image.getAlphaRaster != null
      val pixels = makeIntArray(image.getRaster.getDataBuffer.asInstanceOf[DataBufferByte].getData, hasAlphaChannel)

      new Surface( width, height, pixels )
    }

    require( ((maxWidth - 1) & maxWidth) == 0 )
    require( ((maxHeight - 1) & maxHeight) == 0 )

    val numImages = images.size

    var flip = true
    var sizeX, sizeY = 1

    while( numImages > sizeX * sizeY ) {
      if(flip)
        sizeX *= 2
      else
        sizeY *= 2
      flip = !flip
    }

    Surface.concatVertical( images.grouped(sizeX).map( Surface.concatHorizontal _ ).toSeq )
  }

  private def readImage(filename:String) = {
    val is = getClass.getClassLoader.getResourceAsStream(filename)
    if(is == null)
      throw new java.io.FileNotFoundException("that resource is not available: " + filename)
    ImageIO.read(is)
  }

  def readImageRaster( filename:String ) : Surface = {
    val image = readImage(filename)
    val data = image.getRaster.getDataBuffer.asInstanceOf[DataBufferByte].getData
    val pixels = makeIntArray(data, image.getAlphaRaster != null)
    new Surface(image.getWidth, image.getHeight, pixels)
  }

  def loadAsTexture(filename:String):Texture = {
    import downearth.util.time
    time("load: " + filename) {
      val raster = readImageRaster(filename:String)
      val buffer = BufferUtils.createByteBuffer(raster.width*raster.height*4)
      buffer.asIntBuffer.put(raster.data)
      new Texture(raster.width, raster.height, buffer)
    }
  }
}
