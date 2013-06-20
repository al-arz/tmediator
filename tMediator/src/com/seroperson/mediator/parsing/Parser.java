package com.seroperson.mediator.parsing;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.seroperson.mediator.tori.stuff.Global;
import com.seroperson.mediator.tori.stuff.Player;
import com.seroperson.mediator.tori.stuff.Server;
import com.seroperson.mediator.utils.ThrowHandler;

public class Parser {

	private final static DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	private final static String TORIBASH = "TORIBASH";
	private final static String REGEXP_SERVER = "(\\d{1,3}\\.+\\d{1,3}+\\.+\\d{1,3}+\\.+\\d{1,3}):(\\d{1,}).([A-Za-z0-9]+)";
	private final static String REGEXP_CLIENT = "[^\\s]+([a-zA-Z0-9]{1,30})";
	private final static String REGEXP_CLAN = "[^a-zA-Z0-9]+"; // FIXME arr size = 3
	public final static String CNONE = "none";

	public static Server[] getServers(final ThrowHandler th, final String npservers) {
		final StringTokenizer st = new StringTokenizer(npservers, "\n");
		final ArrayList<Server> servers = new ArrayList<Server>(50);
		boolean prevdescisnull = false;
		try {
			while(st.hasMoreTokens()) {
				final Object[] arr = handleString(npservers, st, prevdescisnull);
				final Server server = (Server) arr[0];
				prevdescisnull = (Boolean) arr[1];
				servers.add(server);
			}
		}
		catch (final Exception e) {
			th.handleThrow(e);
		}

		return servers.toArray(new Server[servers.size()]);
	}

	public static Global getGlobal(String npglobal) throws Throwable {
		if(!npglobal.contains("latest_broadcast")) // FIXME ... (?)
			return null;

		/* message may contain incorrect attribute for xml :c */
		npglobal = npglobal.replaceAll("~<a href=\"member.php\\?u=\\d{0,9}\">+\\p{ASCII}+", "</div>");
		
		final DocumentBuilder docbuilder = factory.newDocumentBuilder();
		final InputStream stream = new ByteArrayInputStream(npglobal.getBytes());
		final String message;
		final String player;
		final String server;
		final Date date;
		final Document doc;

		try {
			doc = docbuilder.parse(stream);
		}
		finally {
			stream.close();
		}

		final NodeList div = doc.getElementsByTagName("div");
		final Node node = div.item(0);

		message = node.getTextContent().trim();
		player = node.getAttributes().getNamedItem("data-username").getTextContent().trim();
		server = node.getAttributes().getNamedItem("data-room").getTextContent().trim();
		date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(node.getAttributes().getNamedItem("data-post_time").getTextContent().trim());

		return new Global(message, date, server, player);
	}

	@SuppressWarnings("unused")
	private static Object[] handleString(final String fullStr, final StringTokenizer st, final boolean prevdescisnull) throws ParseException {
		String currentStr = null;
		{
			if(!prevdescisnull)
				currentStr = st.nextToken();
			if(prevdescisnull || currentStr.startsWith(TORIBASH)) {
				boolean descnull = false;
				String desc = null;
				String room;
				final String mod = null;
				List<Player> players;
				String host;
				int port;
				currentStr = st.nextToken(); // skip INFO

				currentStr = st.nextToken();
				SERVER: {
					final Pattern pattern = Pattern.compile(REGEXP_SERVER);
					final Matcher matcher = pattern.matcher(currentStr);
					matcher.find();
					host = matcher.group(1);
					port = Integer.valueOf(matcher.group(2));
					room = matcher.group(3);
				}

				currentStr = st.nextToken();
				CLIENTS: {
					final Pattern pattern = Pattern.compile(REGEXP_CLIENT);
					final Matcher matcher = pattern.matcher(currentStr.split(";")[1]);
					players = new ArrayList<Player>(5);
					while(matcher.find()) {
						final String withclantag = matcher.group(0);
						String withoutclantag = null;
						String clantag = null;
						if(withclantag.contains("[")) { // or "]"
							final String[] splitted = withclantag.split(REGEXP_CLAN);
							clantag = splitted[1];
							withoutclantag = splitted[2];
						}
						else {
							clantag = CNONE;
							withoutclantag = withclantag;
						}
						players.add(new Player(withoutclantag, clantag, null));
					}
					// TODO (?) #findplayer(string[] allplayers)
				}

				currentStr = st.nextToken();
				NEWGAME: {

				}
				/* TODO handle NEWGAME (?) */
				if(st.hasMoreTokens())
					currentStr = st.nextToken();

				DESC: {
					if(currentStr.matches(TORIBASH)) {
						descnull = true;
						break DESC; // yea, desc can be null -.-
					}
					final String[] arr = currentStr.split(";");
					desc = arr[1].trim();
				}

				final Server server = new Server(desc, room, mod, players.toArray(new Player[players.size()]), host, port);
				for(final Player p : players)
					p.setServer(server);
				return new Object[] { server, new Boolean(descnull) };
			}
			else {
				throw new ParseException(new StringBuilder("Parse error; \n").append(currentStr).toString(), fullStr.indexOf(currentStr));
			}
		}
	}

}
