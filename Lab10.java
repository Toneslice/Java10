import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LibraryApp {

    public static void main(String[] args) throws Exception {

        try (Connection conn = connectMySQL()) {
            conn.setAutoCommit(true);

            System.out.println("╔══════════════════════════════════════════╗");
            System.out.println("║   LIBRARY DB — CRUD DEMO (MySQL)         ║");
            System.out.println("╚══════════════════════════════════════════╝");

            createSchema(conn);

            AuthorDAO authorDAO = new AuthorDAO(conn);
            GenreDAO  genreDAO  = new GenreDAO(conn);
            BookDAO   bookDAO   = new BookDAO(conn);

            // ── INSERT ──────────────────────────────────────────────────
            System.out.println("\n──────── INSERT ────────");
            int a1 = authorDAO.insert("Франко Іван");
            int a2 = authorDAO.insert("Шевченко Тарас");
            int a3 = authorDAO.insert("Коцюбинський Михайло");

            int g1 = genreDAO.insert("Поезія");
            int g2 = genreDAO.insert("Проза");
            int g3 = genreDAO.insert("Драма");

            int b1 = bookDAO.insert("Кобзар",               1840, 5);
            int b2 = bookDAO.insert("Захар Беркут",         1883, 3);
            int b3 = bookDAO.insert("Тіні забутих предків", 1911, 7);
            int b4 = bookDAO.insert("Лісова пісня",         1911, 4);

            bookDAO.addAuthor(b1, a2);  bookDAO.addAuthor(b2, a1);
            bookDAO.addAuthor(b3, a3);  bookDAO.addAuthor(b4, a3);

            bookDAO.addGenre(b1, g1);   bookDAO.addGenre(b2, g2);
            bookDAO.addGenre(b3, g2);   bookDAO.addGenre(b4, g3);
            bookDAO.addGenre(b4, g1);

            // ── READ ────────────────────────────────────────────────────
            System.out.println("\n──────── READ (after INSERT) ────────");
            authorDAO.printAll();  genreDAO.printAll();  bookDAO.printAll();

            // ── UPDATE ──────────────────────────────────────────────────
            System.out.println("\n──────── UPDATE ────────");
            authorDAO.update(a1, "Франко Іван Якович");
            bookDAO.update(b2, "Захар Беркут (2-е вид.)", 1883, 5);

            System.out.println("\n──────── READ (after UPDATE) ────────");
            authorDAO.printAll();  bookDAO.printAll();

            // ── DELETE ──────────────────────────────────────────────────
            System.out.println("\n──────── DELETE ────────");
            genreDAO.delete(g3);
            bookDAO.delete(b4);

            System.out.println("\n──────── READ (after DELETE) ────────");
            genreDAO.printAll();  bookDAO.printAll();

            System.out.println("\n[DONE] All CRUD operations completed successfully.");
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  ПІДКЛЮЧЕННЯ
    // ════════════════════════════════════════════════════════════════
    static Connection connectSQLite() throws SQLException {
        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException e) { throw new SQLException("SQLite driver not found", e); }
        return DriverManager.getConnection("jdbc:sqlite:library.db");
    }
    static Connection connectMySQL() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); //драйвер
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "MySQL JDBC driver not found!\n" +
                            "Завантаж: https://dev.mysql.com/downloads/connector/j/\n" +
                            "та запускай: java -cp .:mysql-connector-j-8.3.0.jar LibraryApp", e);
        }
        String url  = "jdbc:mysql://localhost:3306/library_db?useSSL=false&serverTimezone=UTC";
        String user = "root";       // ← логін
        String pass = "1111";   // ←  пароль
        return DriverManager.getConnection(url, user, pass);
    }
    static Connection connectPostgreSQL() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/library_db",
                "postgres", "password");
    }

    // ════════════════════════════════════════════════════════════════
    //  ДБ
    // ════════════════════════════════════════════════════════════════
    static void createSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
    CREATE TABLE IF NOT EXISTS authors (
        id   INT AUTO_INCREMENT PRIMARY KEY,
        name VARCHAR(255) NOT NULL
    )""");
            st.executeUpdate("""
    CREATE TABLE IF NOT EXISTS genres (
        id   INT AUTO_INCREMENT PRIMARY KEY,
        name VARCHAR(255) NOT NULL UNIQUE
    )""");
            st.executeUpdate("""
    CREATE TABLE IF NOT EXISTS books (
        id       INT AUTO_INCREMENT PRIMARY KEY,
        title    VARCHAR(255) NOT NULL,
        year     INT          NOT NULL,
        quantity INT          NOT NULL DEFAULT 0
    )""");
            st.executeUpdate("""
    CREATE TABLE IF NOT EXISTS book_authors (
        book_id   INT NOT NULL,
        author_id INT NOT NULL,
        PRIMARY KEY (book_id, author_id),
        FOREIGN KEY (book_id)   REFERENCES books(id)   ON DELETE CASCADE,
        FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE CASCADE
    )""");
            st.executeUpdate("""
    CREATE TABLE IF NOT EXISTS book_genres (
        book_id  INT NOT NULL,
        genre_id INT NOT NULL,
        PRIMARY KEY (book_id, genre_id),
        FOREIGN KEY (book_id)  REFERENCES books(id)  ON DELETE CASCADE,
        FOREIGN KEY (genre_id) REFERENCES genres(id) ON DELETE CASCADE
    )""");
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// АВТОРИ
// ══════════════════════════════════════════════════════════════════
class AuthorDAO {
    private final Connection conn;
    AuthorDAO(Connection conn) { this.conn = conn; }

    int insert(String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO authors (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            int id = rs.next() ? rs.getInt(1) : -1;
            System.out.printf("[INSERT] Author  id=%-3d  name='%s'%n", id, name);
            return id;
        }
    }

    List<String[]> findAll() throws SQLException {
        List<String[]> rows = new ArrayList<>();
        ResultSet rs = conn.createStatement()
                .executeQuery("SELECT id, name FROM authors ORDER BY id");
        while (rs.next())
            rows.add(new String[]{ String.valueOf(rs.getInt(1)), rs.getString(2) });
        return rows;
    }

    boolean update(int id, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE authors SET name = ? WHERE id = ?")) {
            ps.setString(1, name); ps.setInt(2, id);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) System.out.printf("[UPDATE] Author  id=%-3d  new name='%s'%n", id, name);
            return ok;
        }
    }

    boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM authors WHERE id = ?")) {
            ps.setInt(1, id);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) System.out.printf("[DELETE] Author  id=%d%n", id);
            return ok;
        }
    }

    void printAll() throws SQLException {
        System.out.println("\n=== AUTHORS ===");
        System.out.printf("%-5s %-30s%n", "ID", "NAME");
        System.out.println("-".repeat(37));
        for (String[] r : findAll())
            System.out.printf("%-5s %-30s%n", r[0], r[1]);
    }
}

// ══════════════════════════════════════════════════════════════════
//  ЖАНРИ
// ══════════════════════════════════════════════════════════════════
class GenreDAO {
    private final Connection conn;
    GenreDAO(Connection conn) { this.conn = conn; }

    int insert(String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO genres (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            int id = rs.next() ? rs.getInt(1) : -1;
            System.out.printf("[INSERT] Genre   id=%-3d  name='%s'%n", id, name);
            return id;
        }
    }

    List<String[]> findAll() throws SQLException {
        List<String[]> rows = new ArrayList<>();
        ResultSet rs = conn.createStatement()
                .executeQuery("SELECT id, name FROM genres ORDER BY id");
        while (rs.next())
            rows.add(new String[]{ String.valueOf(rs.getInt(1)), rs.getString(2) });
        return rows;
    }

    boolean update(int id, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE genres SET name = ? WHERE id = ?")) {
            ps.setString(1, name); ps.setInt(2, id);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) System.out.printf("[UPDATE] Genre   id=%-3d  new name='%s'%n", id, name);
            return ok;
        }
    }

    boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM genres WHERE id = ?")) {
            ps.setInt(1, id);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) System.out.printf("[DELETE] Genre   id=%d%n", id);
            return ok;
        }
    }

    void printAll() throws SQLException {
        System.out.println("\n=== GENRES ===");
        System.out.printf("%-5s %-30s%n", "ID", "NAME");
        System.out.println("-".repeat(37));
        for (String[] r : findAll())
            System.out.printf("%-5s %-30s%n", r[0], r[1]);
    }
}

// ══════════════════════════════════════════════════════════════════
// КНИГИ
// ══════════════════════════════════════════════════════════════════
class BookDAO {
    private final Connection conn;
    BookDAO(Connection conn) { this.conn = conn; }

    int insert(String title, int year, int qty) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO books (title, year, quantity) VALUES (?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title); ps.setInt(2, year); ps.setInt(3, qty);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            int id = rs.next() ? rs.getInt(1) : -1;
            System.out.printf("[INSERT] Book    id=%-3d  title='%s' (%d), qty=%d%n",
                    id, title, year, qty);
            return id;
        }
    }

    void addAuthor(int bookId, int authorId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO book_authors (book_id, author_id) VALUES (?,?)")) {
            ps.setInt(1, bookId); ps.setInt(2, authorId); ps.executeUpdate();
        }
    }

    void addGenre(int bookId, int genreId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO book_genres (book_id, genre_id) VALUES (?,?)")) {
            ps.setInt(1, bookId); ps.setInt(2, genreId); ps.executeUpdate();
        }
    }

    List<String[]> findAll() throws SQLException {
        String sql = """
            SELECT b.id, b.title, b.year, b.quantity,
                   GROUP_CONCAT(DISTINCT a.name) AS authors,
                   GROUP_CONCAT(DISTINCT g.name) AS genres
            FROM books b
            LEFT JOIN book_authors ba ON ba.book_id  = b.id
            LEFT JOIN authors      a  ON a.id        = ba.author_id
            LEFT JOIN book_genres  bg ON bg.book_id  = b.id
            LEFT JOIN genres       g  ON g.id        = bg.genre_id
            GROUP BY b.id ORDER BY b.id""";
        List<String[]> rows = new ArrayList<>();
        ResultSet rs = conn.createStatement().executeQuery(sql);
        while (rs.next())
            rows.add(new String[]{
                    String.valueOf(rs.getInt("id")), rs.getString("title"),
                    String.valueOf(rs.getInt("year")), String.valueOf(rs.getInt("quantity")),
                    rs.getString("authors") != null ? rs.getString("authors") : "—",
                    rs.getString("genres")  != null ? rs.getString("genres")  : "—"
            });
        return rows;
    }

    boolean update(int id, String title, int year, int qty) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE books SET title=?, year=?, quantity=? WHERE id=?")) {
            ps.setString(1, title); ps.setInt(2, year);
            ps.setInt(3, qty);      ps.setInt(4, id);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) System.out.printf("[UPDATE] Book    id=%-3d  title='%s' (%d), qty=%d%n",
                    id, title, year, qty);
            return ok;
        }
    }

    boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM books WHERE id=?")) {
            ps.setInt(1, id);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) System.out.printf("[DELETE] Book    id=%d%n", id);
            return ok;
        }
    }

    void printAll() throws SQLException {
        System.out.println("\n=== BOOKS ===");
        System.out.printf("%-4s %-38s %-6s %-5s %-24s %-20s%n",
                "ID", "TITLE", "YEAR", "QTY", "AUTHORS", "GENRES");
        System.out.println("-".repeat(100));
        for (String[] r : findAll())
            System.out.printf("%-4s %-38s %-6s %-5s %-24s %-20s%n",
                    r[0], r[1], r[2], r[3], r[4], r[5]);
    }
}
