package downearth.rendering.shader

import simplex3d.math.double._
import downearth.rendering.Texture
import downearth.util.AddString

/**
 * User: arne
 * Date: 02.06.13
 * Time: 20:45
 */

class Binding( val program:Program, val attributes:Seq[Attribute], val uniforms:Seq[Uniform] ) extends AddString {

  def addString(sb:StringBuilder) = {
    sb append program
    attributes.addString(sb,"\n\t","\n\t","\n\t")
    uniforms.addString(sb,"\n\t","\n\t","\n\t")
    sb append "\n"
    sb
  }

  def attribute(name:String) = attributes.find( _.name == name ).get

  def bindUniformFloat(name:String, f: => Float) {
    val binding = uniforms.find( _.name == name )
    binding match {
      case Some( uniform:FloatUniform ) =>
        uniform.f = f _
      case _ =>
        System.err.println( "can't bind uniform "+name+" Float" )
    }
  }

  def bindUniformVec4(name:String, f: => ReadVec4) {
    val binding = uniforms.find( _.name == name )
    binding match {
      case Some( uniform:Vec4Uniform ) =>
        uniform.vec = f _
      case _ =>
        System.err.println( "can't bind uniform "+name+" Vec4" )
    }
  }

  def bindUniformVec3(name:String, f: => ReadVec3) {
    val binding = uniforms.find( _.name == name )
    binding match {
      case Some( uniform:Vec3Uniform ) =>
        uniform.vec = f _
      case _ =>
        System.err.println( "can't bind uniform "+name+" Vec3" )
    }
  }

  def bindUniformVec2(name:String, f: => ReadVec2) {
    val binding = uniforms.find( _.name == name )
    binding match {
      case Some( uniform:Vec2Uniform ) =>
        uniform.vec = f _
      case _ =>
        System.err.println( "can't bind uniform "+name+" Vec2" )
    }
  }

  def bindUniformMat4(name:String, f: => ReadMat4) {
    val binding = uniforms.find( _.name == name )
    binding match {
      case Some( uniform:Mat4Uniform ) =>
        uniform.mat = f _
      case _ =>
        System.err.println( "can't bind uniform "+name+" Mat4" )
    }
  }

  def bindUniformSampler2D(name:String, f: => Texture) {
    val binding = uniforms.find( _.name == name )
    binding match {
      case Some( uniform:TextureUniform ) =>
        uniform.texture = f _
      case _ =>
        System.err.println( "can't bind uniform "+name+" Texture" )
    }
  }
}
