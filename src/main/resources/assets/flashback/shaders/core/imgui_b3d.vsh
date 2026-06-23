#version 150

in vec2 Position;
in vec2 UV;
in vec4 Color;

out vec4 vertexColor;
out vec2 texCoord;

layout(std140) uniform UBO {
    mat4 mvp;
};

void main() {
    gl_Position = mvp * vec4(Position, 0.0, 1.0);

    vertexColor = Color;
    texCoord = UV;
}
