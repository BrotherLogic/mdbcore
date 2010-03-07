package uk.co.brotherlogic.mdb.parsers;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import uk.co.brotherlogic.mdb.artist.Artist;
import uk.co.brotherlogic.mdb.artist.GetArtists;
import uk.co.brotherlogic.mdb.categories.Category;
import uk.co.brotherlogic.mdb.categories.GetCategories;
import uk.co.brotherlogic.mdb.format.Format;
import uk.co.brotherlogic.mdb.format.GetFormats;
import uk.co.brotherlogic.mdb.groop.GetGroops;
import uk.co.brotherlogic.mdb.groop.Groop;
import uk.co.brotherlogic.mdb.groop.LineUp;
import uk.co.brotherlogic.mdb.label.GetLabels;
import uk.co.brotherlogic.mdb.label.Label;
import uk.co.brotherlogic.mdb.record.Record;
import uk.co.brotherlogic.mdb.record.Track;

public class DiscogParser
{

	public static void main(String[] args) throws Exception
	{
		DiscogParser parser = new DiscogParser();
		System.out.println(parser.parseDiscogRelease(808723));
	}

	String base = "http://www.discogs.com/release/ID?f=xml&api_key=67668099b8";

	public Record parseDiscogRelease(int id) throws IOException
	{
		try
		{
			URL url = new URL(base.replace("ID", "" + id));
			System.err.println("Reading: " + url);
			SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
			DiscogXMLParser handler = new DiscogXMLParser();
			parser.parse(new GZIPInputStream(url.openStream()), handler);
			return handler.getRecord();
		} catch (SAXException e)
		{
			throw new IOException(e);
		} catch (ParserConfigurationException e)
		{
			throw new IOException(e);
		}
	}
}

class DiscogXMLParser extends DefaultHandler
{
	Record rec = new Record();
	List<LineUp> overallGroops = new LinkedList<LineUp>();
	Track currTrack;

	String text = "";

	// Set up the mapping for the track numbers
	Map<Integer, Integer> highest = new TreeMap<Integer, Integer>();
	{
		highest.put(0, 0);
	}

	private boolean inMain = false;
	private boolean inArtists = false;
	private boolean inLabels = false;
	private boolean inTracks = false;
	private boolean inFormats = false;

	private int quantity = -1;

	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException
	{
		text += new String(ch, start, length);
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException
	{
		String qualName = localName + qName;

		try
		{
			if (inMain)
			{
				if (qualName.equals("artists"))
					inArtists = false;
				else if (qualName.equals("labels"))
					inLabels = false;
				else if (qualName.equals("formats"))
					inFormats = false;
				else if (inFormats && qualName.equals("description"))
				{
					if (text.equals("LP"))
						switch (quantity)
						{
						case 1:
							rec
									.setFormat(GetFormats.create().getFormat(
											"12\""));
							break;
						}
				} else if (qualName.equals("name") && inArtists)
				{
					// Remove the trailing numbers
					if (text.trim().endsWith(")"))
						text = text.substring(0, text.lastIndexOf("("));

					Groop grp = GetGroops.build().getGroopFromShowName(text);
					if (grp == null)
						grp = new Groop(text);
					LineUp lup = null;
					if (grp.getLineUps() == null
							|| grp.getLineUps().size() == 0)
					{
						Artist art = GetArtists.create().getArtistFromShowName(
								text);
						lup = new LineUp(grp);
						lup.addArtist(art);
					} else
						lup = grp.getLineUps().iterator().next();

					overallGroops.add(lup);
				} else if (qualName.equals("title"))
					rec.setTitle(text);
				else if (qualName.equals("description"))
				{
					if (text.equals("Album"))
						rec.setReleaseType(1);
				} else if (qualName.equals("released"))
				{
					if (text.contains("-"))
					{
						String[] elems = text.split("-");
						rec.setYear(Integer.parseInt(elems[0]));
						rec.setReleaseMonth(Integer.parseInt(elems[1]));
					} else
						rec.setYear(Integer.parseInt(text));
				} else if (qualName.equals("genre"))
				{
					Category cat = GetCategories.build().getCategory(text);
					if (text.equals("Rock"))
						cat = GetCategories.build().getCategory("Rock & Pop");
					if (cat != null)
						rec.setCategory(cat);
				}
			} else if (inTracks)
				if (qualName.equals("position"))
				{
					if (Character.isLetter(text.charAt(0)))
					{
						if (text.trim().length() == 1)
							text += "1";
						int offsetCharacter = text.charAt(0) - ('A') + 1;
						text = (offsetCharacter) + "-" + text.substring(1);
					}

					if (text.length() > 0)
						if (text.contains("-"))
						{
							String[] elems = text.split("-");
							int discNumber = Integer.parseInt(elems[0]);
							int trckNumber = Integer.parseInt(elems[1]);
							int number = highest.get(discNumber - 1)
									+ trckNumber;
							currTrack.setTrackNumber(number);

							if (highest.containsKey(discNumber))
								highest.put(discNumber, Math.max(discNumber,
										number));
							else
								highest.put(discNumber, number);
						} else
						{
							int number = Integer.parseInt(text);
							currTrack.setTrackNumber(number);
						}
				} else if (qualName.equals("track"))
				{
					rec.addTrack(currTrack);
					if (currTrack.getLineUps().size() == 0)
						currTrack.addLineUps(overallGroops);
				} else if (qualName.equals("title"))
					currTrack.setTitle(text);
				else if (qualName.equals("duration"))
					if (text.trim().length() > 0)
					{
						String[] elems = text.split(":");
						int lengthInSeconds = Integer.parseInt(elems[0]) * 60
								+ Integer.parseInt(elems[1]);
						currTrack.setLengthInSeconds(lengthInSeconds);
					}

		} catch (SQLException e)
		{
			throw new SAXException(e);
		}
	}

	public Record getRecord()
	{
		// Set the author
		rec.setAuthor(rec.getGroopString());

		return rec;
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException
	{
		String qualName = localName + qName;
		text = "";

		try
		{
			if (inMain)
				if (qualName.equals("artists"))
					inArtists = true;
				else if (qualName.equals("labels"))
					inLabels = true;
				else if (qualName.equals("label") && inLabels)
				{
					String labelName = attributes.getValue("name");
					String catNo = attributes.getValue("catno");
					Label lab = GetLabels.create().getLabel(labelName);
					rec.addLabel(lab);
					rec.addCatNo(catNo);
				} else if (qualName.equals("tracklist"))
				{
					inMain = false;
					inTracks = true;
				} else if (qualName.equals("format"))
				{
					Format form = GetFormats.create().getFormat(
							attributes.getValue("name"));
					if (form != null)
						rec.setFormat(form);

					inFormats = true;
					quantity = Integer.parseInt(attributes.getValue("qty"));
				}

			if (inTracks)
				if (qualName.equals("track"))
					currTrack = new Track();

			if (qualName.equals("release"))
			{
				rec.setDiscogsNum(Integer.parseInt(attributes.getValue("id")));
				inMain = true;
			}
		} catch (SQLException e)
		{
			throw new SAXException(e);
		}

	}

}
