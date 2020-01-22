package ilapin.renderingengine.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import com.google.common.collect.HashMultimap
import ilapin.common.kotlin.safeLet
import ilapin.engine3d.*
import ilapin.renderingengine.*
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
    private val renderingTargetsInfo = HashMap<String, RenderingTargetInfo>()
    private val textureIdsToDelete = IntArray(1)
    private val textureIdsOut = IntArray(1)
    private val frameBufferIdsToDelete = IntArray(1)
    private val renderBufferIdsToDelete = IntArray(1)
    private val frameBufferIdsOut = IntArray(1)
    private val renderBufferIdsOut = IntArray(1)

    private val _ambientColor = Vector3f()

    private val ambientShader = AmbientShader(context)
    private val directionalLightShader = DirectionalLightShader(context)
    private val cameraShader = CameraShader(context)
    private val unlitShader = UnlitShader(context)

    private var displayRenderingTargetInfo: RenderingTargetInfo? = null

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

    override fun createTextureForRendering(textureName: String, width: Int, height: Int) {
        //generate fbo id
        GLES20.glGenFramebuffers(1, frameBufferIdsOut, 0)
        val frameBufferId = frameBufferIdsOut[0]

        //generate texture
        GLES20.glGenTextures(1, textureIdsOut, 0)
        val textureId = textureIdsOut[0]
        textureIds[textureName] = textureId

        //generate render buffer
        GLES20.glGenRenderbuffers(1, renderBufferIdsOut, 0)
        val renderBufferId = renderBufferIdsOut[0]

        renderingTargetsInfo[textureName] = RenderingTargetInfo(frameBufferId, renderBufferId, width, height)

        //Bind Frame buffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferId)
        //Bind texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        //Define texture parameters
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)

        //Bind render buffer and define buffer dimension
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, renderBufferId)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height)

        //Attach texture FBO color attachment
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            textureId,
            0
        )
        //Attach render buffer to depth attachment
        GLES20.glFramebufferRenderbuffer(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_DEPTH_ATTACHMENT,
            GLES20.GL_RENDERBUFFER,
            renderBufferId
        )

        //we are done, reset
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
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
        val renderingTargetInfo = renderingTargetsInfo[textureName]

        renderingTargetInfo?.renderBufferId?.let {
            renderBufferIdsToDelete[0] = it
            GLES20.glDeleteRenderbuffers(1, renderBufferIdsToDelete, 0)
        }

        textureIdsToDelete[0] = getTextureId(textureName)
        GLES20.glDeleteTextures(1, textureIdsToDelete, 0)

        renderingTargetInfo?.frameBufferId?.let {
            frameBufferIdsToDelete[0] = it
            GLES20.glDeleteFramebuffers(1, frameBufferIdsToDelete, 0)
        }

        textureIds.remove(textureName)
        renderingTargetsInfo.remove(textureName)
    }

    override fun getDeviceCameraTextureName() = "androidCameraPreviewTexture"

    fun getTextureIdOrFallback(textureName: String): Int {
        return textureIds[textureName] ?: getTextureId(FALLBACK_TEXTURE_NAME)
    }

    fun getTextureId(textureName: String): Int {
        return textureIds[textureName] ?: throw IllegalArgumentException("Unknown texture name: $textureName")
    }

    fun render() {
        safeLet(sceneProvider(), displayRenderingTargetInfo) { scene, displayRenderingTargetInfo ->
            renderingTargetsInfo.entries.forEach { entry ->
                renderToTarget(scene.getRenderingTargetCameras(entry.key), entry.value)
            }
            renderToTarget(scene.cameras, displayRenderingTargetInfo)
        }
    }

    fun onScreenConfigUpdate(width: Int, height: Int) {
        displayRenderingTargetInfo = RenderingTargetInfo(null, null, width, height)
        sceneProvider.invoke()?.onScreenConfigUpdate(width, height)
    }

    private fun renderToTarget(cameras: List<CameraComponent>, renderingTargetInfo: RenderingTargetInfo) {
        renderingTargetInfo.frameBufferId?.let { GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, it) }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        cameras.forEach { camera ->
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)

            cameraToMeshRenderers[camera].forEach {
                GLES20.glViewport(0, 0, renderingTargetInfo.width, renderingTargetInfo.height)
                if (camera is PerspectiveCameraComponent) {
                    camera.aspect =
                        renderingTargetInfo.width.toFloat() / renderingTargetInfo.height.toFloat()
                }
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

        renderingTargetInfo.frameBufferId?.let { GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0) }
    }

    private fun deleteTextureIfExists(textureName: String) {
        val renderingTargetInfo = renderingTargetsInfo[textureName]

        renderingTargetInfo?.renderBufferId?.let {
            renderBufferIdsToDelete[0] = it
            GLES20.glDeleteRenderbuffers(1, renderBufferIdsToDelete, 0)
        }

        textureIds[textureName]?.let {
            textureIdsToDelete[0] = it
            GLES20.glDeleteTextures(1, textureIdsToDelete, 0)
        }

        renderingTargetInfo?.frameBufferId?.let {
            frameBufferIdsToDelete[0] = it
            GLES20.glDeleteFramebuffers(1, frameBufferIdsToDelete, 0)
        }

        textureIds.remove(textureName)
        renderingTargetsInfo.remove(textureName)
    }

    companion object {

        private const val FALLBACK_TEXTURE_NAME = "fallbackTexture"
    }
}