package ilapin.renderingengine.android

import android.opengl.GLES20
import ilapin.engine3d.DirectionalLightComponent
import ilapin.engine3d.GameObjectComponent
import ilapin.engine3d.MaterialComponent

class UniformFillingVisitor(private val renderingEngine: RenderingEngine) {

    private val bufferFloatArray = FloatArray(4)

    var material: MaterialComponent? = null
    var light: GameObjectComponent? = null

    fun visitAmbientShader(shader: AmbientShader) {
        val currentMaterial = material ?: return

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderingEngine.getTextureIdOrFallback(currentMaterial.textureName))
        GLES20.glGetUniformLocation(shader.program, "textureUniform").takeIf { it >= 0 }?.let { textureHandle ->
            GLES20.glUniform1i(textureHandle, 0)
        }

        GLES20.glGetUniformLocation(shader.program, "ambientColor").takeIf { it >= 0 }?.let { ambientColorHandle ->
            GLES20.glUniform3f(
                ambientColorHandle,
                renderingEngine.ambientColor.x(),
                renderingEngine.ambientColor.y(),
                renderingEngine.ambientColor.z()
            )
        }
    }
    
    fun visitUnlitShader(shader: UnlitShader) {
        val currentMaterial = material ?: return

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderingEngine.getTextureIdOrFallback(currentMaterial.textureName))
        GLES20.glGetUniformLocation(shader.program, "textureUniform").takeIf { it >= 0 }?.let { textureHandle ->
            GLES20.glUniform1i(textureHandle, 0)
        }
    }

    fun visitDirectionalLightShader(shader: DirectionalLightShader) {
        val currentMaterial = material ?: return
        val currentDirectionalLight = light as DirectionalLightComponent? ?: return

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderingEngine.getTextureIdOrFallback(currentMaterial.textureName))
        GLES20.glGetUniformLocation(shader.program, "textureUniform").takeIf { it >= 0 }?.let { textureHandle ->
            GLES20.glUniform1i(textureHandle, 0)
        }

        GLES20
            .glGetUniformLocation(shader.program, "directionalLightUniform.color")
            .takeIf { it >= 0 }?.let { colorHandle ->
                bufferFloatArray[0] = currentDirectionalLight.color.x()
                bufferFloatArray[1] = currentDirectionalLight.color.y()
                bufferFloatArray[2] = currentDirectionalLight.color.z()
                GLES20.glUniform3fv(colorHandle, 1, bufferFloatArray, 0)
            }

        GLES20
            .glGetUniformLocation(shader.program, "directionalLightUniform.direction")
            .takeIf { it >= 0 }?.let { directionHandle ->
                bufferFloatArray[0] = currentDirectionalLight.direction.x()
                bufferFloatArray[1] = currentDirectionalLight.direction.y()
                bufferFloatArray[2] = currentDirectionalLight.direction.z()
                GLES20.glUniform3fv(directionHandle, 1, bufferFloatArray, 0)
            }
    }

    fun visitCameraShader(shader: CameraShader) {
        val currentMaterial = material ?: return

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderingEngine.getTextureIdOrFallback(currentMaterial.textureName))
        GLES20.glGetUniformLocation(shader.program, "textureUniform").takeIf { it >= 0 }?.let { textureHandle ->
            GLES20.glUniform1i(textureHandle, 0)
        }
    }
}