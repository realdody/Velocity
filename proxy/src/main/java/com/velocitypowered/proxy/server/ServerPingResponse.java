/*
 * Copyright (C) 2018-2024 Velocity Contributors
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

package com.velocitypowered.proxy.server;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.server.ServerPing;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Internal wrapper for ServerPing that preserves backend-specific status data,
 * both in the JSON payload and as trailing packet data.
 */
public record ServerPingResponse(
    ServerPing ping,
    @Nullable JsonObject statusJson,
    byte @Nullable [] trailingData) {
  /**
   * Creates a ServerPingResponse with no preserved backend data.
   *
   * @param ping the server ping
   */
  public ServerPingResponse(ServerPing ping) {
    this(ping, null, null);
  }

  /**
   * Creates a ServerPingResponse with trailing packet data but no preserved
   * status JSON.
   *
   * @param ping the server ping
   * @param trailingData trailing packet data from the backend server
   */
  public ServerPingResponse(ServerPing ping, byte @Nullable [] trailingData) {
    this(ping, null, trailingData);
  }

  /**
   * Checks if this response has trailing data.
   *
   * @return true if trailing data is present
   */
  public boolean hasTrailingData() {
    return trailingData != null && trailingData.length > 0;
  }
}
