#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES textureUniform;

varying vec2 uvVarying;

void main() {
    gl_FragColor = texture2D(textureUniform, uvVarying);
}
