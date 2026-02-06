package com.acme.scheduler.common;

import java.sql.Timestamp;
import java.time.Instant;

public class TimeUtils {
	
	public static Instant toInstant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}

}
