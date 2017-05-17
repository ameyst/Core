/*
 * DAPNET CORE PROJECT
 * Copyright (C) 2016
 *
 * Daniel Sialkowski
 *
 * daniel.sialkowski@rwth-aachen.de
 *
 * Institute of High Frequency Technology
 * RWTH AACHEN UNIVERSITY
 * Melatener Str. 25
 * 52074 Aachen
 */

package org.dapnet.core.transmission;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dapnet.core.Settings;
import org.dapnet.core.model.Activation;
import org.dapnet.core.model.Call;
import org.dapnet.core.model.CallSign;
import org.dapnet.core.model.News;
import org.dapnet.core.model.Pager;
import org.dapnet.core.model.Rubric;
import org.dapnet.core.transmission.Message.FunctionalBits;
import org.dapnet.core.transmission.Message.MessagePriority;
import org.dapnet.core.transmission.TransmissionSettings.PagingProtocolSettings;

public class SkyperProtocol implements PagerProtocol {
	private static final PagingProtocolSettings settings = Settings.getTransmissionSettings()
			.getPagingProtocolSettings();
	private static final Logger logger = LogManager.getLogger();
	private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");

	@Override
	public List<Message> createMessagesFromCall(Call call) {
		MessagePriority priority = call.isEmergency() ? MessagePriority.EMERGENCY : MessagePriority.CALL;

		try {
			// Test if message is numeric
			Matcher m = NUMERIC_PATTERN.matcher(call.getText());
			boolean numeric = m.matches();

			List<Message> messages = new ArrayList<>();
			for (CallSign callsign : call.getCallSigns()) {
				for (Pager pager : callsign.getPagers()) {
					if (!pager.isNumeric()) {
						// Pager supports ALPHANUM -> create ALPHANUM message
						messages.add(new Message(call.getText(), pager.getNumber(), priority, FunctionalBits.ALPHANUM));
					} else if (numeric) {
						// Pager does not support ALPHANUM but text is numeric
						// -> create NUMERIC message
						messages.add(new Message(call.getText(), pager.getNumber(), priority, FunctionalBits.NUMERIC));
					} else {
						// Pager does not support ALPHANUM and text is not
						// numeric -> do not create a message
						logger.warn("Pager {} with address {} does not support alphanumeric messages.", pager.getName(),
								pager.getNumber());
					}
				}
			}

			return messages;
		} catch (Exception ex) {
			logger.error("Failed to create messages from call.", ex);
			return null;
		}
	}

	@Override
	public Message createMessageFromTime(Date date) {
		// Generate timeString in necessary format
		String timeString = new SimpleDateFormat("HHmmss   ddMMyy").format(date);

		return new Message(timeString, 2504, Message.MessagePriority.TIME, Message.FunctionalBits.NUMERIC);
	}

	@Override
	public Message createMessageFromRubric(Rubric rubric) {
		// Generate Rubric String: Coding adapted from Funkrufmaster
		StringBuilder sb = new StringBuilder();
		sb.append("1");
		sb.append(String.valueOf((char) (rubric.getNumber() + 0x1f)));
		sb.append(String.valueOf((char) (10 + 0x20)));

		for (int i = 0; i < rubric.getLabel().length(); ++i) {
			sb.append(String.valueOf((char) ((int) rubric.getLabel().charAt(i) + 1)));
		}

		return new Message(sb.toString(), 4512, Message.MessagePriority.RUBRIC, Message.FunctionalBits.ALPHANUM);
	}

	@Override
	public Message createMessageFromNews(News news) {
		// Generate News String: Coding adapted from Funkrufmaster
		StringBuilder sb = new StringBuilder();
		try {
			sb.append(String.valueOf((char) (news.getRubric().getNumber() + 0x1f)));
		} catch (Exception e) {
			return null;
		}

		sb.append(String.valueOf((char) (news.getNumber() + 0x20)));

		for (int i = 0; i < news.getText().length(); ++i) {
			sb.append(String.valueOf((char) ((int) news.getText().charAt(i) + 1)));
		}

		// Create Message
		return new Message(sb.toString(), 4520, Message.MessagePriority.NEWS, Message.FunctionalBits.ALPHANUM);
	}

	@Override
	public Message createMessageFromNewsAsCall(News news) {
		try {
			return new Message(news.getText(), news.getRubric().getAddress(), Message.MessagePriority.CALL,
					Message.FunctionalBits.ALPHANUM);
		} catch (Exception ex) {
			return null;
		}
	}

	@Override
	public Message createMessageFromActivation(Activation activation) {
		List<String> activationCode = Arrays.asList(settings.getActivationCode().split(","));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < activationCode.size(); ++i) {
			List<String> activationSubCode = Arrays.asList(activationCode.get(i).split(" "));
			if (activationSubCode.size() != 3) {
				return null;
			}

			int shift = Integer.parseInt(activationSubCode.get(0));
			int mask = Integer.parseInt(activationSubCode.get(1));
			int offset = Integer.parseInt(activationSubCode.get(2));

			int cInt = ((activation.getNumber() >> shift) & mask) + offset;
			char c = (char) cInt;
			sb.append(String.valueOf(c));
		}

		return new Message(sb.toString(), activation.getNumber(), Message.MessagePriority.ACTIVATION,
				Message.FunctionalBits.ACTIVATION);
	}
}
