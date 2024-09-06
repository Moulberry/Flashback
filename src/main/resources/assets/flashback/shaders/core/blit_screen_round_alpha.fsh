#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    if (color.a > 0.0) {
        color.a = 1.0;
    }
    fragColor = color;
}
