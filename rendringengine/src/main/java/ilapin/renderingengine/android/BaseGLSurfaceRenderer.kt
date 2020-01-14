package ilapin.renderingengine.android

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import ilapin.common.messagequeue.MessageQueue
import ilapin.engine3d.Scene
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

abstract class BaseGLSurfaceRenderer(
    private val context: Context
) : GLSurfaceView.Renderer {

    protected val messageQueue = MessageQueue()

    protected var scene: Scene? = null
    protected lateinit var renderingEngine: RenderingEngine

    override fun onDrawFrame(gl: GL10) {
        messageQueue.update()
        scene?.update()
        renderingEngine.render()
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        renderingEngine.onScreenConfigUpdate(width, height)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig) {
        val renderingEngine = RenderingEngine(context) { scene }
        this.renderingEngine = renderingEngine
        scene = createScene(messageQueue)
    }

    abstract fun createScene(messageQueue: MessageQueue): Scene

    fun putMessage(message: Any) {
        messageQueue.putMessage(message)
    }

    fun putMessageAndWaitForExecution(message: Any) {
        messageQueue.putMessageAndWaitForExecution(message)
    }

    open fun onCleared() {
        scene?.onCleared()
    }
}