package ilapin.renderingengine.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import com.google.common.collect.HashMultimap
import ilapin.renderingengine.*
import ilapin.engine3d.*
import org.joml.Vector3f
import org.joml.Vector3fc

class RenderingEngine(
    private val context: Context,
    private val sceneProvider: () -> Scene?
) : MeshRenderingRepository,
    LightsRenderingRepository,
    RenderingSettingsRepository,
    TextureLoadingRepository,
    TextureRepository,
    SpecialTextureRepository
{
    private val uniformFillingVisitor = UniformFillingVisitor(this)
    private val meshToMeshRenderer = HashMap<MeshComponent, MeshRendererComponent>()
    private val cameraToMeshRenderers = HashMultimap.create<CameraComponent, MeshRendererComponent>()
    private val cameraToDirectionalLights = HashMultimap.create<CameraComponent, DirectionalLightComponent>()

    private val textureIds = HashMap<String, Int>()
    private val textureIdsToDelete = IntArray(1)
    private val textureIdsOut = IntArray(1)

    private val _ambientColor = Vector3f()

    private val ambientShader = AmbientShader(context)
    private val directionalLightShader = DirectionalLightShader(context)
    private val cameraShader = CameraShader(context)
    private val unlitShader = UnlitShader(context)

    val ambientColor: Vector3fc
        get() = _ambientColor

    init {
        GLES20.glClearColor(0f, 0f, 0f, 0f)

        GLES20.glFrontFace(GLES20.GL_CCW)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        createTexture(FALLBACK_TEXTURE_NAME, 1, 1, intArrayOf(0xffff00ff.toInt()))
    }

    override fun setClearColor(red: Float, green: Float, blue: Float, alpha: Float) {
        GLES20.glClearColor(red, green, blue, alpha)
    }

    override fun setAmbientColor(red: Float, green: Float, blue: Float) {
        _ambientColor.set(red, green, blue)
    }

    override fun addMeshToRenderList(camera: CameraComponent, mesh: MeshComponent) {
        val gameObject = mesh.gameObject ?: throw NoParentGameObjectError()
        val meshRendererComponent = MeshRendererComponent(
            uniformFillingVisitor,
            AndroidDisplayMetricsRepository(context)
        )
        gameObject.addComponent(meshRendererComponent)
        meshToMeshRenderer[mesh] = meshRendererComponent
        cameraToMeshRenderers.put(camera, meshRendererComponent)
    }

    override fun removeMeshFromRenderList(camera: CameraComponent, mesh: MeshComponent) {
        val renderer = meshToMeshRenderer.remove(mesh) ?: throw IllegalArgumentException("Can't find mesh renderer to remove")
        if (!cameraToMeshRenderers.remove(camera, renderer)) {
            throw IllegalArgumentException("Can't find mesh renderer's camera to remove")
        }
    }

    override fun addDirectionalLight(camera: CameraComponent, light: DirectionalLightComponent) {
        cameraToDirectionalLights.put(camera, light)
    }

    override fun removeDirectionalLight(camera: CameraComponent, light: DirectionalLightComponent) {
        if (!cameraToDirectionalLights.remove(camera, light)) {
            throw IllegalArgumentException("Can't find directional light's camera to remove")
        }
    }

    override fun loadTexture(textureName: String, path: String) {
        deleteTextureIfExists(textureName)

        GLES20.glGenTextures(1, textureIdsOut, 0)
        textureIds[textureName] = textureIdsOut[0]

        val bitmapStream = context.assets.open(path)
        val bitmap = BitmapFactory.decodeStream(bitmapStream)
        bitmapStream.close()

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIdsOut[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()

        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    override fun createTexture(textureName: String, width: Int, height: Int, data: IntArray) {
        deleteTextureIfExists(textureName)

        GLES20.glGenTextures(1, textureIdsOut, 0)
        textureIds[textureName] = textureIdsOut[0]

        val bitmap = Bitmap.createBitmap(data, width, height, Bitmap.Config.ARGB_8888)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIdsOut[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        bitmap.recycle()

        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun createCameraPreviewTexture(textureName: String) {
        deleteTextureIfExists(textureName)

        GLES20.glGenTextures(1, textureIdsOut, 0)
        textureIds[textureName] = textureIdsOut[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIdsOut[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
    }

    override fun deleteTexture(textureName: String) {
        textureIdsToDelete[0] = getTextureId(textureName)
        GLES20.glDeleteTextures(1, textureIdsToDelete, 0)
    }

    override fun getDeviceCameraTextureName() = "androidCameraPreviewTexture"

    fun getTextureIdOrFallback(textureName: String): Int {
        return textureIds[textureName] ?: getTextureId(FALLBACK_TEXTURE_NAME)
    }

    fun getTextureId(textureName: String): Int {
        return textureIds[textureName] ?: throw IllegalArgumentException("Unknown texture name: $textureName")
    }

    fun render() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        sceneProvider.invoke()?.cameras?.forEach { camera ->
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)

            cameraToMeshRenderers[camera].forEach {
                val material = it.gameObject?.getComponent(MaterialComponent::class.java) ?: return
                if (material.textureName == getDeviceCameraTextureName()) {
                    it.render(camera, cameraShader, null)
                } else {
                    val shader = if (material.isUnlit) {
                        unlitShader
                    } else {
                        ambientShader
                    }
                    it.render(camera, shader, null)
                }
            }

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glDepthMask(false)
            GLES20.glDepthFunc(GLES20.GL_EQUAL)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)

            cameraToDirectionalLights[camera].forEach { light ->
                cameraToMeshRenderers[camera].forEach { meshRenderer ->
                    val material = meshRenderer.gameObject?.getComponent(MaterialComponent::class.java) ?: return
                    if (!material.isUnlit) {
                        meshRenderer.render(camera, directionalLightShader, light)
                    }
                }
            }

            GLES20.glDepthMask(true)
            GLES20.glDepthFunc(GLES20.GL_LESS)
            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    fun onScreenConfigUpdate(width: Int, height: Int) {
        sceneProvider.invoke()?.onScreenConfigUpdate(width, height)
    }

    private fun deleteTextureIfExists(textureName: String) {
        textureIds[textureName]?.let {
            textureIdsToDelete[0] = it
            GLES20.glDeleteTextures(1, textureIdsToDelete, 0)
        }
    }

    companion object {

        private const val FALLBACK_TEXTURE_NAME = "fallbackTexture"
    }
}