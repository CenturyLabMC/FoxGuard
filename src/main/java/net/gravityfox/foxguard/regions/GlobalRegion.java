/*
 * This file is part of FoxGuard, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2015 - 2015. gravityfox - https://gravityfox.net/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.gravityfox.foxguard.regions;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.util.command.CommandSource;
import net.gravityfox.foxguard.commands.util.InternalCommandState;

import javax.sql.DataSource;

/**
 * Created by Fox on 8/17/2015.
 * Project: foxguard
 */
public class GlobalRegion extends RegionBase {

    public static final String NAME = "_global";

    public GlobalRegion() {
        super(NAME);
    }

    @Override
    public void setName(String name) {
        this.name = NAME;
    }

    @Override
    public String getShortTypeName() {
        return "Global";
    }

    @Override
    public String getLongTypeName() {
        return "Global";
    }

    @Override
    public String getUniqueTypeString() {
        return "global";
    }

    @Override
    public Text getDetails(String arguments) {
        return Texts.of("It's global. Nothing to see here. Now move along.");
    }

    @Override
    public void writeToDatabase(DataSource dataSource) {

    }

    @Override
    public boolean modify(String arguments, InternalCommandState state, CommandSource source) {
        return false;
    }

    public boolean isInRegion(int x, int y, int z) {
        return true;
    }

    @Override
    public boolean isInRegion(Vector3i vec) {
        return true;
    }

    @Override
    public boolean isInRegion(double x, double y, double z) {
        return true;
    }

    @Override
    public boolean isInRegion(Vector3d vec) {
        return true;
    }

}
