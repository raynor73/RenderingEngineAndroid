attribute vec3 positionAttribute;
attribute vec3 normalAttribute;
attribute vec2 uvAttribute;

uniform mat4 mvpMatrixUniform;
uniform mat4 modelMatrixUniform;

varying vec3 normalVarying;
varying vec2 uvVarying;

void main() {
    normalVarying = (modelMatrixUniform * vec4(normalAttribute, 0.0)).xyz;
    uvVarying = uvAttribute;
    gl_Position = mvpMatrixUniform * vec4(positionAttribute, 1.0);
}
