package com.acme.scheduler.common;

import java.util.UUID;

/** Simple ID helpers; replaced later with ULID + multi-tenant routing keys. */
public final class Ids {
 private Ids() {}

 public static String newId() {
 return UUID.randomUUID().toString();
 }
}
