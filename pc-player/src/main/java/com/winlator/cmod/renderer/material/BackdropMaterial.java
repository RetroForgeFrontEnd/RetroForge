package com.winlator.cmod.renderer.material;

/**
 * RetroForge: the blurred sides beside a game shown narrower than the screen (a 4:3 game on a 16:9 phone). The
 * game's own picture, scaled up to cover the whole screen, blurred and darkened, so the sides follow the game's
 * colours instead of being black. Drawn only in the side strips; [backdrop] is where the whole scaled-up picture
 * lies (x, y, width, height in screen units) and [radius] the blur's reach in texture units.
 */
public class BackdropMaterial extends ShaderMaterial {
    public BackdropMaterial() {
        setUniformNames("xform", "viewSize", "texture", "backdrop", "radius");
    }

    @Override
    protected String getVertexShader() {
        return
            "uniform float xform[6];\n" +
            "uniform vec2 viewSize;\n" +
            "uniform vec4 backdrop;\n" +
            "attribute vec2 position;\n" +
            "varying vec2 vUV;\n" +

            "void main() {\n" +
                "vec2 transformedPos = applyXForm(position, xform);\n" +
                "vUV = (transformedPos - backdrop.xy) / backdrop.zw;\n" +
                "gl_Position = vec4(2.0 * transformedPos.x / viewSize.x - 1.0, 1.0 - 2.0 * transformedPos.y / viewSize.y, 0.0, 1.0);\n" +
            "}"
        ;
    }

    @Override
    protected String getFragmentShader() {
        return
            "precision mediump float;\n" +

            "uniform sampler2D texture;\n" +
            "uniform vec2 radius;\n" +
            "varying vec2 vUV;\n" +

            // A centre tap and two rings (8 near, 16 far): a soft, wide blur in 25 reads.
            "void main() {\n" +
                "vec3 sum = texture2D(texture, vUV).rgb;\n" +
                "for (int i = 0; i < 8; i++) {\n" +
                    "float a = float(i) * 0.785398;\n" +
                    "sum += texture2D(texture, vUV + vec2(cos(a), sin(a)) * radius * 0.5).rgb;\n" +
                "}\n" +
                "for (int i = 0; i < 16; i++) {\n" +
                    "float a = float(i) * 0.392699 + 0.19635;\n" +
                    "sum += texture2D(texture, vUV + vec2(cos(a), sin(a)) * radius).rgb;\n" +
                "}\n" +
                "gl_FragColor = vec4(sum / 25.0 * 0.45, 1.0);\n" +
            "}"
        ;
    }
}
