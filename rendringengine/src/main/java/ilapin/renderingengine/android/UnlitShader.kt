package ilapin.renderingengine.android

import android.content.Context
import java.nio.charset.Charset

class UnlitShader(context: Context) : Shader() {

    init {
        compile(
            context.assets.open("vertexShader.glsl").readBytes().toString(Charset.defaultCharset()),
            context.assets.open("unlitFragmentShader.glsl").readBytes().toString(Charset.defaultCharset())
        )
    }

    override fun accept(visitor: UniformFillingVisitor) {
        visitor.visitUnlitShader(this)
    }
}