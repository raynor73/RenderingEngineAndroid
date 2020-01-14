precision mediump float;

uniform sampler2D textureUniform;
uniform vec3 ambientColor;

varying vec2 uvVarying;

void main() {
    gl_FragColor = texture2D(textureUniform, uvVarying) * vec4(ambientColor, 1);
}
