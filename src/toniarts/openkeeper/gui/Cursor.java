/*
 * Copyright (C) 2014-2015 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenKeeper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenKeeper.  If not, see <http://www.gnu.org/licenses/>.
 */
package toniarts.openkeeper.gui;

import com.jme3.asset.AssetManager;
import com.jme3.cursors.plugins.JmeCursor;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.regex.Matcher;
import toniarts.openkeeper.tools.convert.AssetsConverter;

public class Cursor extends JmeCursor {

    public int delay = 30;

    /**
     * Generates a static cursor
     *
     * @param assetManager AssetManager
     * @param Filename filename of the cursor in the system
     * @param hotspotx x position of the hotspot (click area)
     * @param hotspoty y position of the hotspot (click area)
     */
    public Cursor(AssetManager assetManager, String Filename, int hotspotx, int hotspoty) {
        this(assetManager, Filename, hotspotx, hotspoty, 1);
    }

    /**
     * Generates an animated cursor
     *
     * @param assetManager AssetManager
     * @param Filename filename of the cursor in the system
     * @param hotspotx x position of the hotspot (click area)
     * @param hotspoty y position of the hotspot (click area)
     * @param frames count of frames
     */
    public Cursor(AssetManager assetManager, String Filename, int hotspotx, int hotspoty, int frames) {
        if (frames < 1) {
            throw new IllegalArgumentException("The cursor needs at least a framecount of 1.");
        }


        Texture tex = assetManager.loadTexture(AssetsConverter.MOUSE_CURSORS_FOLDER.concat(File.separator).concat(Filename).replaceAll(Matcher.quoteReplacement(File.separator), "/"));
        Image img = tex.getImage();
        // width must be a multiple of 16, otherwise the cursor gets distorted
        int width = (img.getWidth() - img.getWidth() % 16) + 16;
        int heightFrame = img.getHeight() / frames;
        int height = (heightFrame - heightFrame % 16) + 16;

        // Image data
        ByteBuffer data = img.getData(0);
        data.rewind();
        IntBuffer image = BufferUtils.createIntBuffer(height * width * frames);
        for (int z = 0; z < frames; z++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = 0;
                    if (x < img.getWidth() && y < heightFrame) {
                        int abgr = data.getInt();
                        argb = ((abgr & 255) << 24) | (abgr >> 8);
                    }

                    image.put(argb);
                }
            }
        }
        image.rewind();

        if (frames > 1) {
            // Delays
            IntBuffer delays = BufferUtils.createIntBuffer(frames);
            for (int i = 0; i < frames; i++) {
                delays.put(delay);
            }

            this.setImagesDelay((IntBuffer) delays.rewind());
        }

        this.setNumImages(frames);
        this.setWidth(width);
        this.setHeight(height);
        this.setxHotSpot(hotspotx);
        this.setyHotSpot(height - hotspoty);
        this.setImagesData(image);
    }
}
