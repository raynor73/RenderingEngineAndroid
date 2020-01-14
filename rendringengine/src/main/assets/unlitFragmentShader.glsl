precision mediump float;

uniform sampler2D textureUniform;

varying vec2 uvVarying;

void main() {
    gl_FragColor = texture2D(textureUniform, uvVarying);
}
