/*
 * Copyright (C) 2018-2021 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.protocol.packet;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import io.netty.buffer.ByteBuf;
import org.checkerframework.checker.nullness.qual.Nullable;

public class StatusResponsePacket implements MinecraftPacket {

  private @Nullable CharSequence status;
  private byte @Nullable [] trailingData;

  public StatusResponsePacket() {
  }

  public StatusResponsePacket(CharSequence status) {
    this.status = status;
    this.trailingData = null;
  }

  public StatusResponsePacket(CharSequence status, byte @Nullable [] trailingData) {
    this.status = status;
    this.trailingData = trailingData;
  }

  public String getStatus() {
    if (status == null) {
      throw new IllegalStateException("Status is not specified");
    }
    return status.toString();
  }

  public byte @Nullable [] getTrailingData() {
    return trailingData;
  }

  public void setTrailingData(byte @Nullable [] trailingData) {
    this.trailingData = trailingData;
  }

  @Override
  public String toString() {
    return "StatusResponse{"
        + "status='" + status + '\''
        + ", trailingDataLength=" + (trailingData != null ? trailingData.length : 0)
        + '}';
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    status = ProtocolUtils.readString(buf, Short.MAX_VALUE);
    // Preserve any trailing bytes (e.g., BetterCompatibilityChecker mod data)
    if (buf.isReadable()) {
      trailingData = new byte[buf.readableBytes()];
      buf.readBytes(trailingData);
    }
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    if (status == null) {
      throw new IllegalStateException("Status is not specified");
    }
    ProtocolUtils.writeString(buf, status);
    // Re-append trailing bytes if present
    if (trailingData != null && trailingData.length > 0) {
      buf.writeBytes(trailingData);
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public int encodeSizeHint(Direction direction, ProtocolVersion version) {
    int hint = ProtocolUtils.stringSizeHint(this.status);
    if (trailingData != null) {
      hint += trailingData.length;
    }
    return hint;
  }
}
