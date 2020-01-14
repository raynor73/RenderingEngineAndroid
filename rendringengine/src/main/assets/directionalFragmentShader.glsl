precision mediump float;

struct DirectionalLight {
    vec3 color;
    vec3 direction;
};

uniform DirectionalLight directionalLightUniform;
uniform sampler2D textureUniform;

varying vec2 uvVarying;
varying vec3 normalVarying;

void main() {
    gl_FragColor =
        texture2D(textureUniform, uvVarying) *
        vec4(directionalLightUniform.color, 1.0) *
        dot(normalize(normalVarying), -directionalLightUniform.direction);
}