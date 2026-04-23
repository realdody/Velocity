/*
 * Copyright (C) 2018-2023 Velocity Contributors
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

package com.velocitypowered.proxy.connection.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.util.VelocityInboundConnection;
import com.velocitypowered.proxy.protocol.packet.LegacyDisconnect;
import com.velocitypowered.proxy.protocol.packet.LegacyPingPacket;
import com.velocitypowered.proxy.protocol.packet.StatusPingPacket;
import com.velocitypowered.proxy.protocol.packet.StatusRequestPacket;
import com.velocitypowered.proxy.protocol.packet.StatusResponsePacket;
import com.velocitypowered.proxy.util.except.QuietRuntimeException;
import io.netty.buffer.ByteBuf;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles server list ping packets from a client.
 */
public class StatusSessionHandler implements MinecraftSessionHandler {

  private static final Set<String> STANDARD_STATUS_KEYS = Set.of(
      "version",
      "players",
      "description",
      "favicon",
      "modinfo");
  private static final Logger logger = LogManager.getLogger(StatusSessionHandler.class);
  private static final QuietRuntimeException EXPECTED_AWAITING_REQUEST = new QuietRuntimeException(
      "Expected connection to be awaiting status request");

  private final VelocityServer server;
  private final MinecraftConnection connection;
  private final VelocityInboundConnection inbound;
  private boolean pingReceived = false;

  StatusSessionHandler(VelocityServer server, VelocityInboundConnection inbound) {
    this.server = server;
    this.connection = inbound.getConnection();
    this.inbound = inbound;
  }

  @Override
  public void activated() {
    if (server.getConfiguration().isShowPingRequests()) {
      logger.info("{} is pinging the server with version {}", this.inbound,
          this.connection.getProtocolVersion());
    }
  }

  @Override
  public boolean handle(LegacyPingPacket packet) {
    if (this.pingReceived) {
      throw EXPECTED_AWAITING_REQUEST;
    }
    this.pingReceived = true;
    server.getServerListPingHandler().getInitialPing(this.inbound)
        .thenCompose(pingResponse ->
            server.getEventManager().fire(new ProxyPingEvent(inbound, pingResponse.ping())))
        .thenAcceptAsync(event -> {
          if (event.getResult().isAllowed()) {
            connection.closeWith(
                LegacyDisconnect.fromServerPing(event.getPing(), packet.getVersion()));
          } else {
            connection.close();
          }
        }, connection.eventLoop())
        .exceptionally((ex) -> {
          logger.error("Exception while handling legacy ping {}", packet, ex);
          return null;
        });
    return true;
  }

  @Override
  public boolean handle(StatusPingPacket packet) {
    connection.closeWith(packet);
    return true;
  }

  @Override
  public boolean handle(StatusRequestPacket packet) {
    if (this.pingReceived) {
      throw EXPECTED_AWAITING_REQUEST;
    }
    this.pingReceived = true;

    this.server.getServerListPingHandler().getInitialPing(inbound)
        .thenCompose(pingResponse -> {
          // Fire ProxyPingEvent with the API ping while keeping backend-specific
          // extensions that are not represented by ServerPing itself.
          return server.getEventManager().fire(new ProxyPingEvent(inbound, pingResponse.ping()))
              .thenApply(event -> new PingEventResult(
                  event,
                  pingResponse.statusJson(),
                  pingResponse.trailingData()));
        })
        .thenAcceptAsync(
            (result) -> {
              if (result.event.getResult().isAllowed()) {
                Gson pingGson = VelocityServer.getPingGsonInstance(connection.getProtocolVersion());
                connection.write(new StatusResponsePacket(
                    serializeStatusJson(
                        pingGson,
                        result.event.getPing(),
                        result.statusJson),
                    result.trailingData));
              } else {
                connection.close();
              }
            },
            connection.eventLoop())
        .exceptionally((ex) -> {
          logger.error("Exception while handling status request {}", packet, ex);
          return null;
        });
    return true;
  }

  @Override
  public void handleUnknown(ByteBuf buf) {
    // what even is going on?
    connection.close(true);
  }

  private static String serializeStatusJson(
      Gson pingGson,
      ServerPing ping,
      JsonObject preservedStatusJson) {
    JsonElement serializedPing = pingGson.toJsonTree(ping);
    if (!serializedPing.isJsonObject()) {
      return pingGson.toJson(ping);
    }
    if (preservedStatusJson == null) {
      return pingGson.toJson(serializedPing);
    }

    JsonObject statusJson = serializedPing.getAsJsonObject();
    for (java.util.Map.Entry<String, JsonElement> entry : preservedStatusJson.entrySet()) {
      if (!STANDARD_STATUS_KEYS.contains(entry.getKey()) && !statusJson.has(entry.getKey())) {
        statusJson.add(entry.getKey(), entry.getValue().deepCopy());
      }
    }
    return pingGson.toJson(statusJson);
  }

  /**
   * Internal record to pass both ProxyPingEvent and preserved backend data
   * through the async chain.
   */
  private record PingEventResult(
      ProxyPingEvent event,
      JsonObject statusJson,
      byte[] trailingData) {
  }
}
