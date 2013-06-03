package downearth.rendering.shader


import org.lwjgl.opengl._
import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL20._
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL13._

import downearth.rendering._
import downearth.util.AddString

/**
 * User: arne
 * Date: 02.06.13
 * Time: 20:47
 */
object Program {
  def apply(name:String)(vertexShaders: VertexShader*)(fragmentShaders: FragmentShader*) = {


    val shaderList = vertexShaders ++ fragmentShaders

    val program = new Program(name)
    program.create()

    for(shader <- shaderList)
      program attach shader

    program.link()

    for(shader <- shaderList)
      program detach shader

    program
  }

  import GL20._
  import GL21._
  import GL30._
  import GL31._
  import GL32._

  def isSampler(_type:Int) = samplers contains _type
  val samplers = Set(
    GL_SAMPLER_1D,
    GL_SAMPLER_2D,
    GL_SAMPLER_3D,
    GL_SAMPLER_CUBE,
    GL_SAMPLER_1D_SHADOW,
    GL_SAMPLER_2D_SHADOW ,
    GL_SAMPLER_1D_ARRAY,
    GL_SAMPLER_2D_ARRAY,
    GL_SAMPLER_1D_ARRAY_SHADOW,
    GL_SAMPLER_2D_ARRAY_SHADOW,
    GL_SAMPLER_2D_MULTISAMPLE,
    GL_SAMPLER_2D_MULTISAMPLE_ARRAY,
    GL_SAMPLER_CUBE_SHADOW,
    GL_SAMPLER_BUFFER,
    GL_SAMPLER_2D_RECT,
    GL_SAMPLER_2D_RECT_SHADOW,
    GL_INT_SAMPLER_1D,
    GL_INT_SAMPLER_2D,
    GL_INT_SAMPLER_3D,
    GL_INT_SAMPLER_CUBE,
    GL_INT_SAMPLER_1D_ARRAY,
    GL_INT_SAMPLER_2D_ARRAY,
    GL_INT_SAMPLER_2D_MULTISAMPLE,
    GL_INT_SAMPLER_2D_MULTISAMPLE_ARRAY,
    GL_INT_SAMPLER_BUFFER,
    GL_INT_SAMPLER_2D_RECT
  )


  import GL41._
  import GL42._

  val shaderTypeString =   Map(
    GL_FLOAT -> "float",
    GL_FLOAT_VEC2 -> "vec2",
    GL_FLOAT_VEC3 -> "vec3",
    GL_FLOAT_VEC4 -> "vec4",
    GL_DOUBLE -> "double",
    GL_DOUBLE_VEC2 -> "dvec2",
    GL_DOUBLE_VEC3 -> "dvec3",
    GL_DOUBLE_VEC4 -> "dvec4",
    GL_INT -> "int",
    GL_INT_VEC2 -> "ivec2",
    GL_INT_VEC3 -> "ivec3",
    GL_INT_VEC4 -> "ivec4",
    GL_UNSIGNED_INT -> "unsigned int",
    GL_UNSIGNED_INT_VEC2 -> "uvec2",
    GL_UNSIGNED_INT_VEC3 -> "uvec3",
    GL_UNSIGNED_INT_VEC4 -> "uvec4",
    GL_BOOL -> "bool",
    GL_BOOL_VEC2 -> "bvec2",
    GL_BOOL_VEC3 -> "bvec3",
    GL_BOOL_VEC4 -> "bvec4",
    GL_FLOAT_MAT2 -> "mat2",
    GL_FLOAT_MAT3 -> "mat3",
    GL_FLOAT_MAT4 -> "mat4",
    GL_FLOAT_MAT2x3 -> "mat2x3",
    GL_FLOAT_MAT2x4 -> "mat2x4",
    GL_FLOAT_MAT3x2 -> "mat3x2",
    GL_FLOAT_MAT3x4 -> "mat3x4",
    GL_FLOAT_MAT4x2 -> "mat4x2",
    GL_FLOAT_MAT4x3 -> "mat4x3",
    GL_DOUBLE_MAT2 -> "dmat2",
    GL_DOUBLE_MAT3 -> "dmat3",
    GL_DOUBLE_MAT4 -> "dmat4",
    GL_DOUBLE_MAT2x3 -> "dmat2x3",
    GL_DOUBLE_MAT2x4 -> "dmat2x4",
    GL_DOUBLE_MAT3x2 -> "dmat3x2",
    GL_DOUBLE_MAT3x4 -> "dmat3x4",
    GL_DOUBLE_MAT4x2 -> "dmat4x2",
    GL_DOUBLE_MAT4x3 -> "dmat4x3",
    GL_SAMPLER_1D -> "sampler1D",
    GL_SAMPLER_2D -> "sampler2D",
    GL_SAMPLER_3D -> "sampler3D",
    GL_SAMPLER_CUBE -> "samplerCube",
    GL_SAMPLER_1D_SHADOW -> "sampler1DShadow",
    GL_SAMPLER_2D_SHADOW -> "sampler2DShadow",
    GL_SAMPLER_1D_ARRAY -> "sampler1DArray",
    GL_SAMPLER_2D_ARRAY -> "sampler2DArray",
    GL_SAMPLER_1D_ARRAY_SHADOW -> "sampler1DArrayShadow",
    GL_SAMPLER_2D_ARRAY_SHADOW -> "sampler2DArrayShadow",
    GL_SAMPLER_2D_MULTISAMPLE -> "sampler2DMS",
    GL_SAMPLER_2D_MULTISAMPLE_ARRAY -> "sampler2DMSArray",
    GL_SAMPLER_CUBE_SHADOW -> "samplerCubeShadow",
    GL_SAMPLER_BUFFER -> "samplerBuffer",
    GL_SAMPLER_2D_RECT -> "sampler2DRect",
    GL_SAMPLER_2D_RECT_SHADOW -> "sampler2DRectShadow",
    GL_INT_SAMPLER_1D -> "isampler1D",
    GL_INT_SAMPLER_2D -> "isampler2D",
    GL_INT_SAMPLER_3D -> "isampler3D",
    GL_INT_SAMPLER_CUBE -> "isamplerCube",
    GL_INT_SAMPLER_1D_ARRAY -> "isampler1DArray",
    GL_INT_SAMPLER_2D_ARRAY -> "isampler2DArray",
    GL_INT_SAMPLER_2D_MULTISAMPLE -> "isampler2DMS",
    GL_INT_SAMPLER_2D_MULTISAMPLE_ARRAY -> "isampler2DMSArray",
    GL_INT_SAMPLER_BUFFER -> "isamplerBuffer",
    GL_INT_SAMPLER_2D_RECT -> "isampler2DRect",
    GL_UNSIGNED_INT_SAMPLER_1D -> "usampler1D",
    GL_UNSIGNED_INT_SAMPLER_2D -> "usampler2D",
    GL_UNSIGNED_INT_SAMPLER_3D -> "usampler3D",
    GL_UNSIGNED_INT_SAMPLER_CUBE -> "usamplerCube",
    GL_UNSIGNED_INT_SAMPLER_1D_ARRAY -> "usampler2DArray",
    GL_UNSIGNED_INT_SAMPLER_2D_ARRAY -> "usampler2DArray",
    GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE -> "usampler2DMS",
    GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE_ARRAY -> "usampler2DMSArray",
    GL_UNSIGNED_INT_SAMPLER_BUFFER -> "usamplerBuffer",
    GL_UNSIGNED_INT_SAMPLER_2D_RECT -> "usampler2DRect",
    GL_IMAGE_1D -> "image1D",
    GL_IMAGE_2D -> "image2D",
    GL_IMAGE_3D -> "image3D",
    GL_IMAGE_2D_RECT -> "image2DRect",
    GL_IMAGE_CUBE -> "imageCube",
    GL_IMAGE_BUFFER -> "imageBuffer",
    GL_IMAGE_1D_ARRAY -> "image1DArray",
    GL_IMAGE_2D_ARRAY -> "image2DArray",
    GL_IMAGE_2D_MULTISAMPLE -> "image2DMS",
    GL_IMAGE_2D_MULTISAMPLE_ARRAY -> "image2DMSArray",
    GL_INT_IMAGE_1D -> "iimage1D",
    GL_INT_IMAGE_2D -> "iimage2D",
    GL_INT_IMAGE_3D -> "iimage3D",
    GL_INT_IMAGE_2D_RECT -> "iimage2DRect",
    GL_INT_IMAGE_CUBE -> "iimageCube",
    GL_INT_IMAGE_BUFFER -> "iimageBuffer",
    GL_INT_IMAGE_1D_ARRAY -> "iimage1DArray",
    GL_INT_IMAGE_2D_ARRAY -> "iimage2DArray",
    GL_INT_IMAGE_2D_MULTISAMPLE -> "iimage2DMS",
    GL_INT_IMAGE_2D_MULTISAMPLE_ARRAY -> "iimage2DMSArray",
    GL_UNSIGNED_INT_IMAGE_1D -> "uimage1D",
    GL_UNSIGNED_INT_IMAGE_2D -> "uimage2D",
    GL_UNSIGNED_INT_IMAGE_3D -> "uimage3D",
    GL_UNSIGNED_INT_IMAGE_2D_RECT -> "uimage2DRect",
    GL_UNSIGNED_INT_IMAGE_CUBE -> "uimageCube",
    GL_UNSIGNED_INT_IMAGE_BUFFER -> "uimageBuffer",
    GL_UNSIGNED_INT_IMAGE_1D_ARRAY -> "uimage1DArray",
    GL_UNSIGNED_INT_IMAGE_2D_ARRAY -> "uimage2DArray",
    GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE -> "uimage2DMS",
    GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE_ARRAY -> "uimage2DMSArray",
    GL_UNSIGNED_INT_ATOMIC_COUNTER -> "atomic_uint"
  )

}

class Program(val name:String) {
  var id = 0

  def attributeLocation(name:CharSequence) = glGetAttribLocation(id,name)
  def uniformLocation(name:CharSequence) = glGetUniformLocation(id,name)

  override def toString = name

  def getBinding = {
    val numUniforms   = glGetProgrami(id, GL_ACTIVE_UNIFORMS)
    val numAttributes = glGetProgrami(id, GL_ACTIVE_ATTRIBUTES)

    val sizetypeBuffer = BufferUtils.createIntBuffer(2)

    val attributes =
      for( location <- 0 until numAttributes ) yield {
        val name = glGetActiveAttrib(id, location, 1000, sizetypeBuffer)
        val size = sizetypeBuffer.get(0)
        val _type = sizetypeBuffer.get(1)
        new Attribute(this, name, location, size, _type)
      }

    var currentSampler = GL_TEXTURE0

    val uniforms:Seq[Uniform] =
      for( location <- 0 until numUniforms) yield {
        val name = glGetActiveUniform(id, location, 1000, sizetypeBuffer)
        val size = sizetypeBuffer.get(0)
        val _type = sizetypeBuffer.get(1)

        val map = Map(
          "program" -> this,
          "name" -> name,
          "location" -> location,
          "position" -> currentSampler,
          "type" -> _type,
          "size" -> size
        )

        if( Program.isSampler(_type) ) {
          val uniform = new TextureUniform(
            position = currentSampler,
            map = map
          )
          currentSampler += 1
          uniform
        }
        else{
          _type match {
            case GL_FLOAT =>
              new FloatUniform(map)
            case GL_FLOAT_VEC2 =>
              new Vec2Uniform(map)
            case GL_FLOAT_VEC3 =>
              new Vec3Uniform(map)
            case GL_FLOAT_VEC4 =>
              new Vec4Uniform(map)
            case GL_FLOAT_MAT4 =>
              new Mat4Uniform(map)
            case _ =>
              throw new NotImplementedError("currently not supported uniform type: "+Program.shaderTypeString(_type) )
          }
        }
      }

    new Binding(this,attributes, uniforms)
  }

  def bind( binding:Binding ) {
    val groupedAttributes = binding.attributes.groupBy( _.bufferBinding.buffer )

    for( (buffer, atList) <- groupedAttributes ) {
      buffer.bind()
      for( binding <- atList ) {
        binding.writeData()
      }
    }

    for( binding <- binding.uniforms ) {
      binding.writeData()
    }
  }

  def create() {
    id = glCreateProgram
  }

  def attach(shader:Shader) {
    glAttachShader(id, shader.id)
  }

  def link() {
    glLinkProgram(id)
    checkLinkStatus()
  }

  def use() {
    glUseProgram(id)
  }

  def use(block: => Unit) {
    glUseProgram(id)
    block
    glUseProgram(0)
  }

  def delete() {
    glDeleteProgram(id)
    id = 0
  }

  def detach(shader:Shader) {
    glDetachShader(id, shader.id)
  }

  def checkLinkStatus() {
    val status = glGetProgrami(id, GL_LINK_STATUS)
    if (status == GL_FALSE)
    {
      val infoLogLength = glGetProgrami(id, GL_INFO_LOG_LENGTH)
      val strInfoLog = glGetProgramInfoLog(id, infoLogLength)
      throw new ShaderLinkError(s"Linker failure: $strInfoLog\n")
    }
  }

  override def finalize() {
    if( id != 0 ) {
      delete()
    }
  }
}
