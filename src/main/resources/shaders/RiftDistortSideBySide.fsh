#version 130

uniform sampler2D uSamplerColor;

// defaults are the values of the first dev kit rendered at 1280 by 800:
uniform vec4 uLensCenter    = vec4(0.287994, 0.5, 0.712006, 0.5);
uniform vec4 uScreenCenter  = vec4(0.25, 0.5,  0.75, 0.5);
uniform vec4 uScale         = vec4(0.25, 0.4,  0.25, 0.4);
uniform vec4 uScaleIn       = vec4(4.0,  2.5,  4.0,  2.5);
uniform vec4 uHmdWarpParam  = vec4(1.0,  0.22, 0.24, 0.0);
uniform vec4 uChromAbParam  = vec4(0.996, -0.004, 1.014, 0.0);
uniform bool uDistort = true;
uniform bool uCorrectChromaticAberation = true;

in vec2 vTexCoord;
out vec4 oColor;

//
// taken and adjusted from the SDK sample:
//
vec4 getDistortedColorAt( in vec2 position, in bool leftEye )
{
    vec2 LensCenter;
    vec2 ScreenCenter;
    vec2 Scale   = uScale.xy;
    vec2 ScaleIn = uScaleIn.xy;
    if (leftEye) {
        // left half
        LensCenter   = uLensCenter.xy;
        ScreenCenter = uScreenCenter.xy;
    } else {
        // right half
        LensCenter   = uLensCenter.zw;
        ScreenCenter = uScreenCenter.zw;
    }

    // vector from the lens center to the current point:
    vec2  theta = (position - LensCenter) * ScaleIn; // Scales to [-1, 1]

    // scaled distance from the lens center:
    float rSq = theta.x * theta.x + theta.y * theta.y;


    vec2  theta1 = theta * (uHmdWarpParam.x + uHmdWarpParam.y * rSq + uHmdWarpParam.z * rSq * rSq + uHmdWarpParam.w * rSq * rSq * rSq);

    // Detect whether blue texture coordinates are out of range since these will scaled out the furthest.
    vec2 thetaBlue = theta1 * (uChromAbParam.z + uChromAbParam.w * rSq);
    vec2 tcBlue = Scale * thetaBlue + LensCenter;
    if (!all(equal(clamp(tcBlue, ScreenCenter-vec2(0.25,0.5), ScreenCenter+vec2(0.25,0.5)), tcBlue)))
    {
        return vec4(0.2);
    }

    // Do green lookup (no scaling).
    vec2  tcGreen = Scale * theta1 + LensCenter;
    vec4  center = texture(uSamplerColor, tcGreen);

    if (!uCorrectChromaticAberation) {
        return center;
    }

    // Now do blue texture lookup.
    float blue = texture(uSamplerColor, tcBlue).b;

    // Do red scale and lookup.
    vec2  thetaRed = theta1 * (uChromAbParam.x + uChromAbParam.y * rSq);
    vec2  tcRed = Scale * thetaRed + LensCenter;
    float red = texture(uSamplerColor, tcRed).r;

    return vec4(red, center.g, blue, center.a);
}



void main()
{
    if (uDistort) {
        bool leftEye = (vTexCoord.x < 0.5);
        oColor = getDistortedColorAt( vTexCoord, leftEye );
    } else {
        oColor = texture(uSamplerColor, vTexCoord);
    }
}

