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
package toniarts.openkeeper.tools.convert;

import java.io.File;
import toniarts.openkeeper.tools.convert.wad.WadFile;
import toniarts.openkeeper.utils.SettingUtils;

/**
 * Simple class to extract all the files from given WAD to given location
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class WadExtractor {

    private static String dkIIFolder;

    public static void main(String[] args) {

        //Take Dungeon Keeper 2 root folder as parameter
        if (args.length != 2 || !new File(args[0]).exists()) {
            dkIIFolder = SettingUtils.getDKIIFolder();
            if (dkIIFolder == null)
            {
                throw new RuntimeException("Please provide Dungeon Keeper II root folder as a first parameter! Second parameter is the extraction target folder!");
            }
        } else {
            dkIIFolder = SettingUtils.fixFilePath(args[0]);
        }

        dkIIFolder = dkIIFolder.concat("data").concat(File.separator);

        //And the destination
        String destination = args[1];
        if (!destination.endsWith(File.separator)) {
            destination = destination.concat(File.separator);
        }

        //Extract the meshes
        WadFile wad = new WadFile(new File(dkIIFolder + "Meshes.WAD"));
        wad.extractFileData(destination.concat("meshes"));
    }
}
