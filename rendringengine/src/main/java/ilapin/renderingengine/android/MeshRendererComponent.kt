package ilapin.renderingengine.android

import android.opengl.GLES20
import ilapin.renderingengine.DisplayMetricsRepository
import ilapin.engine3d.*
import org.joml.Matrix4f
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

class MeshRendererComponent(
    private val uniformFillingVisitor: UniformFillingVisitor,
    displayMetricsRepository: DisplayMetricsRepository
) : GameObjectComponent() {

    companion object {

        private const val COORDINATES_PER_POSITION = 3
        private const val COORDINATES_PER_NORMAL = 3
        private const val COORDINATES_PER_UV = 2
        //private const val COMPONENTS_PER_VERTEX = COORDINATES_PER_POSITION + COORDINATES_PER_NORMAL;
    }

    private var cachedVertexBuffer: Buffer? = null
    private var cachedNormalBuffer: Buffer? = null
    private var cachedUvBuffer: Buffer? = null
    private var cachedIndexBuffer: Buffer? = null
    private var cachedNumberOfIndices: Int? = null

    private val bufferMatrix = Matrix4f()
    private val bufferFloatArray = FloatArray(16)

    private val lineWidth = ceil(displayMetricsRepository.getPixelDensityFactor())

    fun render(
        camera: CameraComponent,
        shader: Shader,
        light: GameObjectComponent?,
        textureRenderingTargetInfo: TextureRenderingTargetInfo? = null
    ) {
        if (!isEnabled) {
            return
        }

        textureRenderingTargetInfo?.let {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, it.frameBufferId)
            GLES20.glViewport(0, 0, it.width, it.height)
        }

        val material = gameObject?.getComponent(MaterialComponent::class.java) ?: return
        val transformation = gameObject?.getComponent(TransformationComponent::class.java) ?: return
        val viewProjectionMatrix = camera.getViewProjectionMatrix() ?: return

        if (cachedVertexBuffer == null) {
            val mesh = gameObject?.getComponent(MeshComponent::class.java) ?: return
            val numberOfVertices = mesh.vertices.size
            val numberOfIndices = mesh.indices.size

            val verticesFloatArray = FloatArray(numberOfVertices * COORDINATES_PER_POSITION)
            val normalsFloatArray = FloatArray(numberOfVertices * COORDINATES_PER_NORMAL)
            val uvsFloatArray = FloatArray(numberOfVertices * COORDINATES_PER_UV)
            for (i in 0 until numberOfVertices) {
                val vertex = mesh.vertices[i]
                verticesFloatArray[i * COORDINATES_PER_POSITION] = vertex.x()
                verticesFloatArray[i * COORDINATES_PER_POSITION + 1] = vertex.y()
                verticesFloatArray[i * COORDINATES_PER_POSITION + 2] = vertex.z()

                val normal = mesh.normals[i]
                normalsFloatArray[i * COORDINATES_PER_NORMAL] = normal.x()
                normalsFloatArray[i * COORDINATES_PER_NORMAL + 1] = normal.y()
                normalsFloatArray[i * COORDINATES_PER_NORMAL + 2] = normal.z()

                val uv = mesh.uvs[i]
                uvsFloatArray[i * COORDINATES_PER_UV] = uv.x()
                uvsFloatArray[i * COORDINATES_PER_UV + 1] = uv.y()
            }
            cachedVertexBuffer = ByteBuffer.allocateDirect(verticesFloatArray.size * BYTES_PER_FLOAT).run {
                order(ByteOrder.nativeOrder())
                asFloatBuffer().apply {
                    put(verticesFloatArray)
                    position(0)
                }
            }
            cachedNormalBuffer = ByteBuffer.allocateDirect(normalsFloatArray.size * BYTES_PER_FLOAT).run {
                order(ByteOrder.nativeOrder())
                asFloatBuffer().apply {
                    put(normalsFloatArray)
                    position(0)
                }
            }
            cachedUvBuffer = ByteBuffer.allocateDirect(uvsFloatArray.size * BYTES_PER_FLOAT).run {
                order(ByteOrder.nativeOrder())
                asFloatBuffer().apply {
                    put(uvsFloatArray)
                    position(0)
                }
            }

            cachedNumberOfIndices = numberOfIndices

            val indicesShortArray = ShortArray(numberOfIndices)
            for (i in 0 until numberOfIndices) {
                indicesShortArray[i] = mesh.indices[i].toShort()
            }
            cachedIndexBuffer = ByteBuffer.allocateDirect(numberOfIndices * BYTES_PER_SHORT).run {
                order(ByteOrder.nativeOrder())
                asShortBuffer().apply {
                    put(indicesShortArray)
                    position(0)
                }
            }
        }
        val vertexBuffer = cachedVertexBuffer ?: return
        val normalBuffer = cachedNormalBuffer ?: return
        val uvBuffer = cachedUvBuffer ?: return
        val indexBuffer = cachedIndexBuffer ?: return
        val numberOfIndices = cachedNumberOfIndices ?: return

        GLES20.glUseProgram(shader.program)

        val positionHandle = GLES20.glGetAttribLocation(shader.program, "positionAttribute")
        val normalHandle = GLES20.glGetAttribLocation(shader.program, "normalAttribute")
        val uvHandle = GLES20.glGetAttribLocation(shader.program, "uvAttribute")

        GLES20.glEnableVertexAttribArray(positionHandle)
        normalHandle.takeIf { it >= 0 }?.let { GLES20.glEnableVertexAttribArray(normalHandle) }
        GLES20.glEnableVertexAttribArray(uvHandle)

        GLES20.glVertexAttribPointer(
            positionHandle,
            COORDINATES_PER_POSITION,
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBuffer
        )
        normalHandle.takeIf { it >= 0 }?.let {
            GLES20.glVertexAttribPointer(
                normalHandle,
                COORDINATES_PER_NORMAL,
                GLES20.GL_FLOAT,
                false,
                0,
                normalBuffer
            )
        }
        GLES20.glVertexAttribPointer(
            uvHandle,
            COORDINATES_PER_UV,
            GLES20.GL_FLOAT,
            false,
            0,
            uvBuffer
        )

        uniformFillingVisitor.material = material
        uniformFillingVisitor.light = light
        shader.accept(uniformFillingVisitor)

        GLES20.glGetUniformLocation(shader.program, "mvpMatrixUniform").also { mvpMatrixHandle ->
            viewProjectionMatrix.get(bufferMatrix)
            bufferMatrix.translate(transformation.position)
            bufferMatrix.scale(transformation.scale)
            bufferMatrix.rotate(transformation.rotation)
            bufferMatrix.get(bufferFloatArray)
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, bufferFloatArray, 0)
        }

        GLES20.glGetUniformLocation(shader.program, "modelMatrixUniform").also { handle ->
            bufferMatrix.identity()
            bufferMatrix.scale(transformation.scale)
            bufferMatrix.rotate(transformation.rotation)
            bufferMatrix.get(bufferFloatArray)
            GLES20.glUniformMatrix4fv(handle, 1, false, bufferFloatArray, 0)
        }

        if (material.isDoubleSided) {
            GLES20.glDisable(GLES20.GL_CULL_FACE)
        } else {
            GLES20.glEnable(GLES20.GL_CULL_FACE)
        }
        val mode = if (material.isWireframe) {
            GLES20.GL_LINE_LOOP
        } else {
            GLES20.GL_TRIANGLES
        }
        GLES20.glLineWidth(lineWidth)
        GLES20.glDrawElements(mode, numberOfIndices, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

        GLES20.glDisableVertexAttribArray(uvHandle)
        normalHandle.takeIf { it >= 0 }?.let { GLES20.glDisableVertexAttribArray(normalHandle) }
        GLES20.glDisableVertexAttribArray(positionHandle)

        textureRenderingTargetInfo?.let { GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0) }
    }
}