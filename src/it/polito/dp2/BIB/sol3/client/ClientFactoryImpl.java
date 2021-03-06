package it.polito.dp2.BIB.sol3.client;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import it.polito.dp2.BIB.ass3.Bookshelf;
import it.polito.dp2.BIB.ass3.Client;
import it.polito.dp2.BIB.ass3.DestroyedBookshelfException;
import it.polito.dp2.BIB.ass3.ItemReader;
import it.polito.dp2.BIB.ass3.ServiceException;
import it.polito.dp2.BIB.ass3.TooManyItemsException;
import it.polito.dp2.BIB.ass3.UnknownItemException;
import it.polito.dp2.BIB.sol3.model.MyBookshelf;
import it.polito.dp2.BIB.sol3.service.jaxb.BookshelfType;
import it.polito.dp2.BIB.sol3.service.jaxb.BookshelvesType;
import it.polito.dp2.BIB.sol3.service.jaxb.Item;
import it.polito.dp2.BIB.sol3.service.jaxb.Items;

public class ClientFactoryImpl implements Client {
	javax.ws.rs.client.Client client;
	WebTarget target;
	static String uri = "http://localhost:8080/BiblioSystem/rest/";
	static String urlProperty = "it.polito.dp2.BIB.ass3.URL";
	static String portProperty = "it.polito.dp2.BIB.ass3.PORT";

	public ClientFactoryImpl(URI uri) {
		this.uri = uri.toString();

		client = ClientBuilder.newClient();
		target = client.target(uri).path("biblio");
	}

	@Override
	public Bookshelf createBookshelf(String name) throws ServiceException {
		Response reply = target.path("bookshelf").queryParam("name", name).request(MediaType.APPLICATION_JSON)
				.post(null);
		reply.bufferEntity();
		if (reply.getStatus() != 201)
			throw new ServiceException();
		else {
			try {
				BookshelfType result = reply.readEntity(BookshelfType.class);
				MyBookshelf bookshelf = new MyBookshelf(result.getName());
				bookshelf.setId(result.getId().intValue());
				bookshelf.setSelf(result.getSelf());
				return bookshelf;
			} catch (DestroyedBookshelfException e) {
				throw new ServiceException();
			}
		}
	}

	@Override
	public Set<Bookshelf> getBookshelfs(String name) throws ServiceException {
		Set<Bookshelf> result = new HashSet<>();
		Response reply = target.path("bookshelf").queryParam("keyword", name).request(MediaType.APPLICATION_JSON).get();
		reply.bufferEntity();
		if (reply.getStatus() != 200)
			throw new ServiceException();
		else {
			BookshelvesType bookshelves = reply.readEntity(BookshelvesType.class);
			bookshelves.getBookshelfType().forEach(b -> {
				try {
					MyBookshelf temp = new MyBookshelf(b.getName());
					temp.setId(b.getId().intValue());
					temp.setSelf(b.getSelf());
					b.getItem().forEach(i -> {
						ItemReaderImpl item = new ItemReaderImpl(i);
						try {
							temp.addItemLocally(item, BigInteger.valueOf(item.getId()));
						} catch (DestroyedBookshelfException | UnknownItemException | TooManyItemsException
								| ServiceException e) {
							e.printStackTrace();
						}
					});
					result.add(temp);
				} catch (DestroyedBookshelfException e) {
					e.printStackTrace();
				}
			});
		}
		return result;
	}

	@Override
	public Set<ItemReader> getItems(String keyword, int since, int to) throws ServiceException {
		Set<ItemReader> itemSet = new HashSet<>();
		Response reply = this.target.path("items").queryParam("keyword", keyword).queryParam("beforeInclusive", to)
				.queryParam("afterInclusive", since).request(MediaType.APPLICATION_JSON_TYPE).get();
		reply.bufferEntity();
		if (reply.getStatus() != 200)
			throw new ServiceException();
		reply.readEntity(Items.class).getItem().forEach(i -> itemSet.add(new ItemReaderImpl(i)));
		return itemSet;
	}

	private static void printItems() throws ServiceException {
		Set<ItemReader> set = mainClient.getItems("", 0, 3000);
		System.out.println("Items returned: " + set.size());

		// For each Item print related data
		for (ItemReader item : set) {
			System.out.println("Title: " + item.getTitle());
			if (item.getSubtitle() != null)
				System.out.println("Subtitle: " + item.getSubtitle());
			System.out.print("Authors: ");
			String[] authors = item.getAuthors();
			System.out.print(authors[0]);
			for (int i = 1; i < authors.length; i++)
				System.out.print(", " + authors[i]);
			System.out.println(";");

			Set<ItemReader> citingItems = item.getCitingItems();
			System.out.println("Cited by " + citingItems.size() + " items:");
			for (ItemReader citing : citingItems) {
				System.out.println("- " + citing.getTitle());
			}
			printLine('-');

		}
		printBlankLine();
	}

	private static void printBlankLine() {
		System.out.println(" ");
	}

	private static void printLine(char c) {
		System.out.println(makeLine(c));
	}

	private static StringBuffer makeLine(char c) {
		StringBuffer line = new StringBuffer(132);

		for (int i = 0; i < 132; ++i) {
			line.append(c);
		}
		return line;
	}

	static ClientFactoryImpl mainClient;

	public static void main(String[] args) {
		System.setProperty("it.polito.dp2.BIB.BibReaderFactory", "it.polito.dp2.BIB.Random.BibReaderFactoryImpl");
		String customUri = System.getProperty(urlProperty);
		String customPort = System.getProperty(portProperty);
		if (customUri != null)
			uri = customUri;

		try {
			mainClient = new ClientFactoryImpl(new URI(uri));
			// printItems();
			Bookshelf bookshelf1 = mainClient.createBookshelf("Primo scaffale");
			Bookshelf bookshelf2 = mainClient.createBookshelf("Secondo scaffale");
			Bookshelf bookshelf3 = mainClient.createBookshelf("Terzo scaffale");
			Set<Bookshelf> allBookshelves = mainClient.getBookshelfs("scaffale");
			Set<ItemReader> allItemsSet = mainClient.getItems("method", 1900, 2020);
			System.out.println("I have " + allItemsSet.size() + " items inside the Set");
			List<ItemReader> allItems = mainClient.getItems("method", 1900, 2020).stream().collect(Collectors.toList());
			System.out.println("I have " + allItems.size() + " items");
			List<ItemReader> itemsAdded = new ArrayList<>();
			try {
				System.out.println("bookshelf 1: " + bookshelf1.getName());
				System.out.println("bookshelf 2: " + bookshelf2.getName());
				System.out.println("bookshelf 3: " + bookshelf3.getName());
				System.out.println("Here I have some bookshelves:");
				for (Bookshelf b : allBookshelves) {
					System.out.println(b.getName());
				}
				int counter = 0;
				for (int i = 0; i < allItems.size(); i++) {
					// if (counter < 61) {
					if (counter < 60) {
						if (i % 3 == 1) {
							bookshelf1.addItem(allItems.get(i));
							itemsAdded.add(allItems.get(i));
							counter++;
						} else if (i % 3 == 2) {
							bookshelf2.addItem(allItems.get(i));
							itemsAdded.add(allItems.get(i));
							counter++;
						} else if (i % 3 == 0) {
							bookshelf3.addItem(allItems.get(i));
							itemsAdded.add(allItems.get(i));
							counter++;
						}
					} else
						break;
				}
				System.out.println("There are " + bookshelf1.getItems().size() + " items inside bookshelf1");
				System.out.println("There are " + bookshelf2.getItems().size() + " items inside bookshelf2");
				System.out.println("There are " + bookshelf3.getItems().size() + " items inside bookshelf3");
				bookshelf1.getItems();
				bookshelf2.getItems();
				bookshelf1.getItems();
				System.out.println("Reads 1: " + bookshelf1.getNumberOfReads());
				System.out.println("Reads 2: " + bookshelf2.getNumberOfReads());
				System.out.println("Reads 3: " + bookshelf3.getNumberOfReads());
				// bookshelf1.removeItem(null);
				for (int i = 0; i < itemsAdded.size(); i++) {
					if (i % 3 == 1) {
						bookshelf1.removeItem(itemsAdded.get(i));
					} else if (i % 3 == 2) {
						bookshelf2.removeItem(itemsAdded.get(i));
					} else if (i % 3 == 0) {
						bookshelf3.removeItem(itemsAdded.get(i));
					}
				}
				bookshelf1.getItems();
				bookshelf2.getItems();
				bookshelf3.getItems();
				System.out.println("Reads 1: " + bookshelf1.getNumberOfReads());
				System.out.println("Reads 2: " + bookshelf2.getNumberOfReads());
				System.out.println("Reads 3: " + bookshelf3.getNumberOfReads());
				bookshelf1.destroyBookshelf();
				bookshelf2.destroyBookshelf();
				bookshelf3.destroyBookshelf();
				System.out.println("No more bookshelves:");
				// bookshelf1.getItems();
				// bookshelf1.destroyBookshelf();
			} catch (DestroyedBookshelfException | UnknownItemException | TooManyItemsException e) {
				e.printStackTrace();
			}
		} catch (URISyntaxException | ServiceException e) {
			e.printStackTrace();
		}

	}

}
