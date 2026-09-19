#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 Position;
layout(location = 1) in vec2 UV;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 texCoord;

layout(std140) uniform UBO {
    mat4 mvp;
};

void main() {
    gl_Position = mvp * vec4(Position, 0.0, 1.0);

    vertexColor = Color;
    texCoord = UV;
}
