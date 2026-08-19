#version 150

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

layout(std140) uniform TransformDepth {
    int isZZeroToOne;
    float near;
    float far;
};

void main() {
    float z = texture(InSampler, texCoord).r;

    // Convert [-1, 1] to [0, 1]
    if (isZZeroToOne == 0) {
        z = z * 2.0 - 1.0;
    }

    // Linearize depth
    float linear = near * far / (far + z * (near - far));
    float normalized = linear / max(near, far);
    if (near > far) {
        normalized = 1.0 - normalized;
    }

    fragColor = vec4(normalized, 0.0, 0.0, 0.0);
}
