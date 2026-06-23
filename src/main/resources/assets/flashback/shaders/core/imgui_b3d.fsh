#version 150

in vec4 vertexColor;
in vec2 texCoord;

uniform sampler2D Sampler0;

out vec4 fragColor;

void main() {
    fragColor = vertexColor * texture(Sampler0, texCoord);
}
