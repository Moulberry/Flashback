#version 150

uniform sampler2D InSampler;

uniform vec4 ColorModulator;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    fragColor = color * ColorModulator;
}
