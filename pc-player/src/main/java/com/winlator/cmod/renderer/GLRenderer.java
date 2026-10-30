package com.winlator.cmod.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.math.XForm;
import com.winlator.cmod.renderer.material.BackdropMaterial;
import com.winlator.cmod.renderer.material.CursorMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;
import com.winlator.cmod.renderer.material.WindowMaterial;
import com.winlator.cmod.widget.XServerView;
import com.winlator.cmod.xserver.Bitmask;
import com.winlator.cmod.xserver.Cursor;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowAttributes;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRenderer implements GLSurfaceView.Renderer, WindowManager.OnWindowModificationListener, Pointer.OnPointerMotionListener {
    public final XServerView xServerView;
    private final XServer xServer;
    public final VertexAttribute quadVertices = new VertexAttribute("position", 2);
    private final float[] tmpXForm1 = XForm.getInstance();
    private final float[] tmpXForm2 = XForm.getInstance();
    private final CursorMaterial cursorMaterial = new CursorMaterial();
    private final WindowMaterial windowMaterial = new WindowMaterial();
    private final BackdropMaterial backdropMaterial = new BackdropMaterial();
    private final float[] tmpXForm3 = XForm.getInstance();
    private boolean blurredSides = true;
    public final ViewTransformation viewTransformation = new ViewTransformation();
    private final Drawable rootCursorDrawable;
    private final ArrayList<RenderableWindow> renderableWindows = new ArrayList<>();
    private String forceFullscreenWMClass = null;
    private boolean fullscreen = false;
    private boolean toggleFullscreen = false;
    public boolean viewportNeedsUpdate = true;
    private boolean cursorVisible = true;
    private boolean rootWindowDownsized = false;
    private boolean screenOffsetYRelativeToCursor = false;
    private String[] unviewableWMClasses = null;
    private float magnifierZoom = 1.0f;
    private boolean magnifierEnabled = true;
    public int surfaceWidth;
    public int surfaceHeight;
    private final EffectComposer effectComposer;

    public GLRenderer(XServerView xServerView, XServer xServer) {
        this.xServerView = xServerView;
        this.xServer = xServer;
        this.effectComposer = new EffectComposer(this);
        rootCursorDrawable = createRootCursorDrawable();

        quadVertices.put(new float[]{
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f
        });

        xServer.windowManager.addOnWindowModificationListener(this);
        xServer.pointer.addOnPointerMotionListener(this);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GPUImage.checkIsSupported();

        GLES20.glFrontFace(GLES20.GL_CCW);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        viewTransformation.update(width, height, xServer.screenInfo.width, xServer.screenInfo.height);
        viewportNeedsUpdate = true;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (toggleFullscreen) {
            fullscreen = !fullscreen;
            toggleFullscreen = false;
            viewportNeedsUpdate = true;

        }

        drawFrame();
    }

    public void drawFrame() {
        boolean xrFrame = false;
        boolean xrImmersive = false;

        // Update the viewport if necessary
        if (viewportNeedsUpdate && magnifierEnabled) {
            if (fullscreen) {
                GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
            }
            else {
                GLES20.glViewport(viewTransformation.viewOffsetX, viewTransformation.viewOffsetY, viewTransformation.viewWidth, viewTransformation.viewHeight);
            }
            viewportNeedsUpdate = false;
        }

        // Clear the screen before drawing
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Apply basic transformations and draw windows
        if (magnifierEnabled) {
            // Apply magnifier transformations if enabled
            float pointerX = 0;
            float pointerY = 0;
            float magnifierZoom = !screenOffsetYRelativeToCursor ? this.magnifierZoom : 1.0f;

            if (magnifierZoom != 1.0f) {
                pointerX = Mathf.clamp(xServer.pointer.getX() * magnifierZoom - xServer.screenInfo.width * 0.5f, 0, xServer.screenInfo.width * Math.abs(1.0f - magnifierZoom));
            }

            if (screenOffsetYRelativeToCursor || magnifierZoom != 1.0f) {
                float scaleY = magnifierZoom != 1.0f ? Math.abs(1.0f - magnifierZoom) : 0.5f;
                float offsetY = xServer.screenInfo.height * (screenOffsetYRelativeToCursor ? 0.25f : 0.5f);
                pointerY = Mathf.clamp(xServer.pointer.getY() * magnifierZoom - offsetY, 0, xServer.screenInfo.height * scaleY);
            }

            XForm.makeTransform(tmpXForm2, -pointerX, -pointerY, magnifierZoom, magnifierZoom, 0);
        } else {
            if (!fullscreen) {
                int pointerY = 0;
                if (screenOffsetYRelativeToCursor) {
                    short halfScreenHeight = (short)(xServer.screenInfo.height / 2);
                    pointerY = Mathf.clamp(xServer.pointer.getY() - halfScreenHeight / 2, 0, halfScreenHeight);
                }

                XForm.makeTransform(tmpXForm2, viewTransformation.sceneOffsetX, viewTransformation.sceneOffsetY - pointerY, viewTransformation.sceneScaleX, viewTransformation.sceneScaleY, 0);

                GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
                GLES20.glScissor(viewTransformation.viewOffsetX, viewTransformation.viewOffsetY, viewTransformation.viewWidth, viewTransformation.viewHeight);
            } else {
                XForm.identity(tmpXForm2);
            }
        }

        // Render windows without effects
        renderWindows(xrImmersive);

        // Render cursor if enabled
        if (cursorVisible && !rootWindowDownsized) renderCursor();

        // Disable scissor test if magnifier is disabled and not in fullscreen mode
        if (!magnifierEnabled && !fullscreen) {
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        }

        // Apply all the effects using EffectComposer
        if (effectComposer.hasEffects()) {
            effectComposer.render();  // <-- This line applies the effects
        }

    }


    @Override
    public void onMapWindow(Window window) {
        xServerView.queueEvent(this::updateScene);
        xServerView.requestRender();
    }

    @Override
    public void onUnmapWindow(Window window) {
        xServerView.queueEvent(this::updateScene);
        xServerView.requestRender();
    }

    @Override
    public void onChangeWindowZOrder(Window window) {
        xServerView.queueEvent(this::updateScene);
        xServerView.requestRender();
    }

    @Override
    public void onUpdateWindowContent(Window window) {
        xServerView.requestRender();
    }

    @Override
    public void onUpdateWindowGeometry(final Window window, boolean resized) {
        if (resized) {
            xServerView.queueEvent(this::updateScene);
        }
        else xServerView.queueEvent(() -> updateWindowPosition(window));
        xServerView.requestRender();
    }

    @Override
    public void onUpdateWindowAttributes(Window window, Bitmask mask) {
        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) xServerView.requestRender();
    }

    @Override
    public void onPointerMove(short x, short y) {
        xServerView.requestRender();
    }

    private void renderDrawable(Drawable drawable, int x, int y, ShaderMaterial material) {
        renderDrawable(drawable, x, y, material, false);
    }

    private void renderDrawable(Drawable drawable, int x, int y, ShaderMaterial material, boolean forceFullscreen) {
        if (drawable == null) return;
        synchronized (drawable.renderLock) {
            Texture texture = drawable.getTexture();
            texture.updateFromDrawable(drawable);

            if (forceFullscreen) {
                short newHeight = (short)Math.min(xServer.screenInfo.height, ((float)xServer.screenInfo.width / drawable.width) * drawable.height);
                short newWidth = (short)(((float)newHeight / drawable.height) * drawable.width);
                float left = (xServer.screenInfo.width - newWidth) * 0.5f;
                float top = (xServer.screenInfo.height - newHeight) * 0.5f;
                if (blurredSides && material == windowMaterial && (left >= 1 || top >= 1)) {
                    renderBackdrop(texture, drawable.width, drawable.height, left, top);
                }
                XForm.set(tmpXForm1, left, top, newWidth, newHeight);
                if (material == windowMaterial) {
                    drawnStretch = new Stretch(x, y, drawable.width, drawable.height, left, top, (float)newHeight / drawable.height);
                }
            }
            else XForm.set(tmpXForm1, x, y, drawable.width, drawable.height);

            XForm.multiply(tmpXForm1, tmpXForm1, tmpXForm2);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.getTextureId());
            GLES20.glUniform1i(material.getUniformLocation("texture"), 0);
            GLES20.glUniform1fv(material.getUniformLocation("xform"), tmpXForm1.length, tmpXForm1, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, quadVertices.count());
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
    }

    /**
     * RetroForge: fills the strips beside a stretched window narrower than the screen with its own picture, scaled
     * up to cover the screen, blurred and darkened (see BackdropMaterial). Leaves the window material in use.
     */
    private void renderBackdrop(Texture texture, int width, int height, float sideWidth, float bandHeight) {
        float screenW = xServer.screenInfo.width;
        float screenH = xServer.screenInfo.height;
        float scale = Math.max(screenW / width, screenH / height);
        float backdropW = width * scale;
        float backdropH = height * scale;

        backdropMaterial.use();
        GLES20.glUniform2f(backdropMaterial.getUniformLocation("viewSize"), screenW, screenH);
        GLES20.glUniform4f(backdropMaterial.getUniformLocation("backdrop"),
            (screenW - backdropW) * 0.5f, (screenH - backdropH) * 0.5f, backdropW, backdropH);
        GLES20.glUniform2f(backdropMaterial.getUniformLocation("radius"), 0.06f, 0.06f * width / height);
        quadVertices.bind(backdropMaterial.programId);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.getTextureId());
        GLES20.glUniform1i(backdropMaterial.getUniformLocation("texture"), 0);
        // The strips left and right of the window, then above and below it.
        float[][] strips = {
            {0, 0, sideWidth, screenH}, {screenW - sideWidth, 0, sideWidth, screenH},
            {sideWidth, 0, screenW - 2 * sideWidth, bandHeight}, {sideWidth, screenH - bandHeight, screenW - 2 * sideWidth, bandHeight},
        };
        for (float[] strip : strips) {
            if (strip[2] < 1 || strip[3] < 1) continue;
            XForm.set(tmpXForm3, strip[0], strip[1], strip[2], strip[3]);
            XForm.multiply(tmpXForm3, tmpXForm3, tmpXForm2);
            GLES20.glUniform1fv(backdropMaterial.getUniformLocation("xform"), tmpXForm3.length, tmpXForm3, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, quadVertices.count());
        }
        quadVertices.disable();

        windowMaterial.use();
        GLES20.glUniform2f(windowMaterial.getUniformLocation("viewSize"), screenW, screenH);
        quadVertices.bind(windowMaterial.programId);
    }

    /** RetroForge: whether the sides beside a narrower game are blurred (true) or black. */
    public void setBlurredSides(boolean blurredSides) {
        this.blurredSides = blurredSides;
        xServerView.requestRender();
    }

    private void renderWindows(boolean forceFullscreen) {
        windowMaterial.use();
        GLES20.glUniform2f(windowMaterial.getUniformLocation("viewSize"), xServer.screenInfo.width, xServer.screenInfo.height);
        quadVertices.bind(windowMaterial.programId);

        boolean singleWindow = forceFullscreen;
        drawnStretch = null;
        try (XLock lock = xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {
            rootWindowDownsized = false;
            if (fullscreen && !renderableWindows.isEmpty()) {
                RenderableWindow root = renderableWindows.get(0);
                if ((root.content.width < xServer.screenInfo.width) || (root.content.height < xServer.screenInfo.height)) {
                    rootWindowDownsized = true;
                    singleWindow = true;
                }
            }
            if (singleWindow && !renderableWindows.isEmpty()) {
                RenderableWindow window = renderableWindows.get(renderableWindows.size() - 1);
                renderDrawable(window.content, window.rootX, window.rootY, windowMaterial, true);
            } else {
                for (RenderableWindow window : renderableWindows) {
                    renderDrawable(window.content, window.rootX, window.rootY, windowMaterial, window.forceFullscreen);
                }
            }
        }

        quadVertices.disable();
        stretch = drawnStretch;

        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Log.e("GLRenderer", "OpenGL Error: " + error);
        }

    }

    private void renderCursor() {
        cursorMaterial.use();
        GLES20.glUniform2f(cursorMaterial.getUniformLocation("viewSize"), xServer.screenInfo.width, xServer.screenInfo.height);
        quadVertices.bind(cursorMaterial.programId);

        try (XLock lock = xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {
            Window pointWindow = xServer.inputDeviceManager.getPointWindow();
            Cursor cursor = pointWindow != null ? pointWindow.attributes.getCursor() : null;
            short x = xServer.pointer.getClampedX();
            short y = xServer.pointer.getClampedY();
            // RetroForge: over a stretched window, draw the cursor where that spot of the window is shown.
            Stretch s = stretch;
            if (s != null && s.contains(x, y)) {
                short sx = (short)(s.left + (x - s.rootX) * s.scale);
                y = (short)(s.top + (y - s.rootY) * s.scale);
                x = sx;
            }

            if (cursor != null) {
                if (cursor.isVisible()) renderDrawable(cursor.cursorImage, x - cursor.hotSpotX, y - cursor.hotSpotY, cursorMaterial);
            }
            else renderDrawable(rootCursorDrawable, x, y, cursorMaterial);
        }

        quadVertices.disable();
    }

    public void toggleFullscreen() {
        toggleFullscreen = true;
        xServerView.requestRender();
    }

    private Drawable createRootCursorDrawable() {
        Context context = xServerView.getContext();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), com.retroforge.pcplayer.R.drawable.pc_cursor, options);
        return Drawable.fromBitmap(bitmap);
    }

    private void updateScene() {
        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
            renderableWindows.clear();
            collectRenderableWindows(xServer.windowManager.rootWindow, xServer.windowManager.rootWindow.getX(), xServer.windowManager.rootWindow.getY());
        }
    }

    private void collectRenderableWindows(Window window, int x, int y) {
        if (!window.attributes.isMapped()) return;
        if (window != xServer.windowManager.rootWindow) {
            boolean viewable = true;

            if (unviewableWMClasses != null) {
                String wmClass = window.getClassName();
                for (String unviewableWMClass : unviewableWMClasses) {
                    if (wmClass.contains(unviewableWMClass)) {
                        if (window.attributes.isEnabled()) window.disableAllDescendants();
                        viewable = false;
                        break;
                    }
                }
            }

            if (viewable) {
                if (forceFullscreenWMClass != null) {
                    short width = window.getWidth();
                    short height = window.getHeight();
                    boolean forceFullscreen= false;

                    if (width >= 320 && height >= 200 && width < xServer.screenInfo.width && height < xServer.screenInfo.height) {
                        Window parent = window.getParent();
                        boolean parentHasWMClass = parent.getClassName().contains(forceFullscreenWMClass);
                        boolean hasWMClass = window.getClassName().contains(forceFullscreenWMClass);
                        if (hasWMClass) {
                            forceFullscreen = !parentHasWMClass && window.getChildCount() == 0;
                        }
                        else {
                            short borderX = (short)(parent.getWidth() - width);
                            short borderY = (short)(parent.getHeight() - height);
                            // RetroForge: Wine draws Direct3D/OpenGL/Vulkan into a client window the size of the game's
                            // window, inside it; that client window is the one shown stretched.
                            if (parentHasWMClass && parent.getChildCount() == 1 && borderX == 0 && borderY == 0) {
                                forceFullscreen = true;
                                removeRenderableWindow(parent);
                            }
                            else if (parent.getChildCount() == 1 && borderX > 0 && borderY > 0 && borderX <= 12) {
                                forceFullscreen = true;
                                removeRenderableWindow(parent);
                            }
                        }
                    }

                    renderableWindows.add(new RenderableWindow(window.getContent(), x, y, forceFullscreen));
                }
                else renderableWindows.add(new RenderableWindow(window.getContent(), x, y));
            }
        }

        for (Window child : window.getChildren()) {
            collectRenderableWindows(child, child.getX() + x, child.getY() + y);
        }
    }

    private void removeRenderableWindow(Window window) {
        for (int i = 0; i < renderableWindows.size(); i++) {
            if (renderableWindows.get(i).content == window.getContent()) {
                renderableWindows.remove(i);
                break;
            }
        }
    }

    private void updateWindowPosition(Window window) {
        for (RenderableWindow renderableWindow : renderableWindows) {
            if (renderableWindow.content == window.getContent()) {
                renderableWindow.rootX = window.getRootX();
                renderableWindow.rootY = window.getRootY();
                break;
            }
        }
    }

    public void setCursorVisible(boolean cursorVisible) {
        this.cursorVisible = cursorVisible;
        xServerView.requestRender();
    }

    public boolean isCursorVisible() {
        return cursorVisible;
    }

    public boolean isScreenOffsetYRelativeToCursor() {
        return screenOffsetYRelativeToCursor;
    }

    public void setScreenOffsetYRelativeToCursor(boolean screenOffsetYRelativeToCursor) {
        this.screenOffsetYRelativeToCursor = screenOffsetYRelativeToCursor;
        xServerView.requestRender();
    }

    /**
     * RetroForge: where a window drawn stretched to the screen (force fullscreen) really is on the X screen, and
     * where and how big it's shown, so a touch on the picture can be sent to the X position under it.
     */
    private static final class Stretch {
        final int rootX, rootY, width, height;
        final float left, top, scale;

        Stretch(int rootX, int rootY, int width, int height, float left, float top, float scale) {
            this.rootX = rootX; this.rootY = rootY; this.width = width; this.height = height;
            this.left = left; this.top = top; this.scale = scale;
        }

        boolean contains(int x, int y) {
            return x >= rootX && y >= rootY && x < rootX + width && y < rootY + height;
        }
    }

    private Stretch drawnStretch = null;
    private volatile Stretch stretch = null;

    /** RetroForge: turns a point on the shown picture (X screen units) into the X screen point under it. */
    public int[] shownToScreen(int x, int y) {
        Stretch s = stretch;
        if (s == null) return new int[]{x, y};
        float wx = (x - s.left) / s.scale;
        float wy = (y - s.top) / s.scale;
        if (wx < 0 || wy < 0 || wx >= s.width || wy >= s.height) {
            // Outside the stretched window (the black bars): the nearest point of its edge.
            wx = Math.max(0, Math.min(s.width - 1, wx));
            wy = Math.max(0, Math.min(s.height - 1, wy));
        }
        return new int[]{s.rootX + (int)wx, s.rootY + (int)wy};
    }

    public String getForceFullscreenWMClass() {
        return forceFullscreenWMClass;
    }

    public void setForceFullscreenWMClass(String forceFullscreenWMClass) {
        this.forceFullscreenWMClass = forceFullscreenWMClass;
    }

    public String[] getUnviewableWMClasses() {
        return unviewableWMClasses;
    }

    public void setUnviewableWMClasses(String... unviewableWMNames) {
        this.unviewableWMClasses = unviewableWMNames;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public float getMagnifierZoom() {
        return magnifierZoom;
    }

    public void setMagnifierZoom(float magnifierZoom) {
        this.magnifierZoom = magnifierZoom;
        xServerView.requestRender();
    }

    public int getSurfaceWidth() {
        return surfaceWidth;
    }

    public int getSurfaceHeight() {
        return surfaceHeight;
    }

    public boolean isViewportNeedsUpdate() {
        return viewportNeedsUpdate;
    }

    public void setViewportNeedsUpdate(boolean viewportNeedsUpdate) {
        this.viewportNeedsUpdate = viewportNeedsUpdate;
    }

    public VertexAttribute getQuadVertices() {
        return quadVertices;
    }

    public EffectComposer getEffectComposer (){
        return effectComposer;
    }

    private void renderWindowEffect(Drawable drawable, int x, int y, ShaderMaterial material) {
        // Implement the rendering effect logic here
        synchronized (drawable.renderLock) {
            Texture texture = drawable.getTexture();
            texture.updateFromDrawable(drawable);

            XForm.set(tmpXForm1, x, y, drawable.width, drawable.height);
            XForm.multiply(tmpXForm1, tmpXForm1, tmpXForm2);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.getTextureId());
            if (GLES20.glIsTexture(texture.getTextureId()) == false) {
                Log.e("GLRenderer", "Invalid texture binding!");
            }

            GLES20.glUniform1i(material.getUniformLocation("texture"), 0);
            GLES20.glUniform1fv(material.getUniformLocation("xform"), tmpXForm1.length, tmpXForm1, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, quadVertices.count());
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
    }


}
