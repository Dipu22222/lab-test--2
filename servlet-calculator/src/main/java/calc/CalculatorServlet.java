package calc;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Calculator servlet. It draws the calculator UI (display + keypad),
 * evaluates the expression on the server, saves it to SQLite,
 * and shows the saved history table.
 */
public class CalculatorServlet extends HttpServlet {

    // Stored in the folder the app is started from (the project folder in IntelliJ)
    private static final Path DB_FILE = Paths.get("calculator.db").toAbsolutePath();
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE;

    @Override
    public void init() throws ServletException {
        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println("SQLite database file: " + DB_FILE);
            try (Connection c = DriverManager.getConnection(DB_URL);
                 Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS history ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "expression TEXT NOT NULL,"
                        + "result REAL NOT NULL,"
                        + "created_at TEXT DEFAULT (datetime('now','localtime')))");
            }
        } catch (Exception e) {
            throw new ServletException("Could not initialise database", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        render(resp, req.getParameter("e"), req.getParameter("r"), null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if ("clear".equals(req.getParameter("action"))) {
            try (Connection c = DriverManager.getConnection(DB_URL);
                 Statement s = c.createStatement()) {
                s.executeUpdate("DELETE FROM history");
            } catch (SQLException e) {
                render(resp, null, null, "Database error: " + e.getMessage());
                return;
            }
            resp.sendRedirect("/");
            return;
        }

        String expr = req.getParameter("expr");
        expr = expr == null ? "" : expr.replace(" ", "");
        if (expr.isEmpty() || expr.length() > 100) {
            render(resp, null, null, "Please enter a calculation.");
            return;
        }

        double result;
        try {
            result = new Parser(expr).parse();
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                throw new ArithmeticException("Result is not a finite number");
            }
        } catch (RuntimeException e) {
            render(resp, expr, null, e.getMessage());
            return;
        }

        try (Connection c = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO history(expression, result) VALUES (?,?)")) {
            ps.setString(1, expr);
            ps.setDouble(2, result);
            ps.executeUpdate();
        } catch (SQLException e) {
            render(resp, expr, null, "Database error: " + e.getMessage());
            return;
        }

        // Post/Redirect/Get so a browser refresh doesn't save the calculation twice
        resp.sendRedirect("/?e=" + URLEncoder.encode(expr, "UTF-8")
                + "&r=" + URLEncoder.encode(fmt(result), "UTF-8"));
    }

    // ------------------------------------------------------------------ page

    private static final String CSS = String.join("\n",
        "*{box-sizing:border-box}",
        "body{font-family:system-ui,Segoe UI,Arial,sans-serif;background:#eef1f6;margin:0;padding:24px 12px;color:#222}",
        "h1{text-align:center;font-size:22px;margin:0 0 16px}",
        ".nav{text-align:center;margin:-8px 0 16px}",
        ".nav a{color:#2563eb;margin:0 10px;text-decoration:none;font-weight:600}",
        ".calc{width:100%;max-width:340px;margin:0 auto;background:#1c1f26;border-radius:22px;padding:18px;box-shadow:0 12px 30px rgba(0,0,0,.25)}",
        ".screen{background:#12141a;border-radius:14px;padding:12px 16px;margin-bottom:14px;text-align:right;min-height:96px}",
        ".small{color:#8b93a7;font-size:15px;min-height:20px;overflow-x:auto;white-space:nowrap}",
        ".main{color:#fff;font-size:42px;font-weight:300;line-height:1.3;overflow-x:auto;white-space:nowrap}",
        ".err{color:#ff6b6b;font-size:14px;min-height:18px;margin-top:2px}",
        ".keys{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}",
        ".keys button{font-size:22px;padding:16px 0;border:0;border-radius:14px;background:#2d313c;color:#fff;cursor:pointer}",
        ".keys button:hover{filter:brightness(1.2)}",
        ".keys button:active{transform:scale(.96)}",
        ".keys .op{background:#ff9f0a}",
        ".keys .fn{background:#a5a9b5;color:#111}",
        ".keys .eq{background:#30d158}",
        ".keys .wide{grid-column:span 2}",
        ".hist{max-width:640px;margin:34px auto 0;background:#fff;border-radius:14px;padding:16px;box-shadow:0 2px 10px rgba(0,0,0,.08)}",
        ".hist h2{margin:0 0 4px;font-size:18px}",
        ".hint{color:#6b7280;font-size:13px;margin:0 0 10px}",
        "table{width:100%;border-collapse:collapse}",
        "th,td{border-bottom:1px solid #e5e7eb;padding:8px;text-align:left;font-size:14px}",
        "th{background:#f3f4f6}",
        ".clear{margin-top:12px;background:#dc2626;color:#fff;border:0;border-radius:8px;padding:9px 14px;font-size:14px;cursor:pointer}");

    private static final String JS = String.join("\n",
        "var ops='+-*/%';",
        "var cur='', fresh=false;",
        "var app=document.getElementById('app');",
        "var disp=document.getElementById('main'), small=document.getElementById('small');",
        "var form=document.getElementById('f'), hid=document.getElementById('expr');",
        "function pretty(s){return s.split('*').join(String.fromCharCode(215)).split('/').join(String.fromCharCode(247)).split('-').join(String.fromCharCode(8722));}",
        "function show(){disp.textContent = cur==='' ? '0' : pretty(cur);}",
        "function press(k){",
        "  if(k==='C'){cur='';small.textContent='';fresh=false;}",
        "  else if(k==='B'){ if(fresh){cur='';fresh=false;} else {cur=cur.slice(0,-1);} }",
        "  else if(k==='='){ if(cur===''){return;} hid.value=cur; form.submit(); return; }",
        "  else if(ops.indexOf(k)>=0){",
        "    if(cur===''){ if(k==='-'){cur='-';} }",
        "    else { var last=cur.slice(-1);",
        "      if(ops.indexOf(last)>=0){ if(cur.length>1){cur=cur.slice(0,-1)+k;} }",
        "      else {cur+=k;} }",
        "    fresh=false;",
        "  } else {",
        "    if(fresh){cur='';fresh=false;}",
        "    if(k==='.'){",
        "      var i=cur.length-1;",
        "      while(i>=0 && ops.indexOf(cur.charAt(i))<0){i--;}",
        "      var num=cur.slice(i+1);",
        "      if(num.indexOf('.')>=0){return;}",
        "      if(num===''){cur+='0';}",
        "    }",
        "    if(cur.length<60){cur+=k;}",
        "  }",
        "  show();",
        "}",
        "var btns=document.querySelectorAll('button[data-k]');",
        "for(var j=0;j<btns.length;j++){ btns[j].onclick=function(){press(this.getAttribute('data-k'));}; }",
        "document.addEventListener('keydown',function(e){",
        "  var k=e.key;",
        "  if(k.length===1 && '0123456789.+-*/%'.indexOf(k)>=0){press(k);e.preventDefault();}",
        "  else if(k==='Enter'||k==='='){press('=');e.preventDefault();}",
        "  else if(k==='Backspace'){press('B');}",
        "  else if(k==='Escape'||k==='c'||k==='C'){press('C');}",
        "});",
        "var E=app.getAttribute('data-e'), R=app.getAttribute('data-r');",
        "if(R){cur=R;fresh=true;small.textContent=pretty(E)+' =';}",
        "else if(E){cur=E;}",
        "show();");

    private void render(HttpServletResponse resp, String expr, String result, String error)
            throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        out.println("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        out.println("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        out.println("<title>Calculator</title><style>" + CSS + "</style></head><body>");
        out.println("<h1>Calculator</h1>");
        out.println("<div class='nav'><a href='/game'>Play DIPU \u2013 MATH WHIZ</a>"
                + "<a href='/game?p=leaderboard'>Leaderboard</a></div>");

        // Calculator
        out.println("<div class='calc' id='app' data-e='" + esc(expr) + "' data-r='" + esc(result) + "'>");
        out.println("<div class='screen'><div class='small' id='small'></div>"
                + "<div class='main' id='main'>0</div>"
                + "<div class='err'>" + esc(error) + "</div></div>");
        out.println("<div class='keys'>");
        out.println(key("C", "C", "fn") + key("B", "&#9003;", "fn") + key("%", "%", "fn") + key("/", "&divide;", "op"));
        out.println(key("7", "7", "") + key("8", "8", "") + key("9", "9", "") + key("*", "&times;", "op"));
        out.println(key("4", "4", "") + key("5", "5", "") + key("6", "6", "") + key("-", "&minus;", "op"));
        out.println(key("1", "1", "") + key("2", "2", "") + key("3", "3", "") + key("+", "+", "op"));
        out.println(key("0", "0", "wide") + key(".", ".", "") + key("=", "=", "eq"));
        out.println("</div>");
        out.println("<form id='f' method='post' action='/'><input type='hidden' name='expr' id='expr'></form>");
        out.println("</div>");

        // History table from the database
        out.println("<div class='hist'><h2>Database: history</h2>");
        out.println("<p class='hint'>Saved in " + esc(DB_FILE.toString()) + "</p>");
        try (Connection c = DriverManager.getConnection(DB_URL);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT id, expression, result, created_at FROM history ORDER BY id DESC")) {
            out.println("<table><tr><th>ID</th><th>Expression</th><th>Result</th><th>Time</th></tr>");
            int rows = 0;
            while (rs.next()) {
                rows++;
                String e = rs.getString("expression").replace("*", "\u00d7").replace("/", "\u00f7");
                out.println("<tr><td>" + rs.getInt("id") + "</td><td>" + esc(e) + "</td><td>"
                        + esc(fmt(rs.getDouble("result"))) + "</td><td>"
                        + esc(rs.getString("created_at")) + "</td></tr>");
            }
            if (rows == 0) out.println("<tr><td colspan='4'>No calculations yet.</td></tr>");
            out.println("</table>");
        } catch (SQLException e) {
            out.println("<p style='color:#b91c1c'>Could not read database: " + esc(e.getMessage()) + "</p>");
        }
        out.println("<form method='post' action='/'><input type='hidden' name='action' value='clear'>"
                + "<button class='clear' type='submit'>Clear history</button></form></div>");

        out.println("<script>" + JS + "</script></body></html>");
    }

    private static String key(String value, String label, String cls) {
        return "<button type='button' class='" + cls + "' data-k='" + value + "'>" + label + "</button>";
    }

    /** 5.0 -> "5", 0.1+0.2 -> "0.3" (rounded to 10 decimals). */
    private static String fmt(double d) {
        BigDecimal bd = new BigDecimal(d).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros();
        return bd.signum() == 0 ? "0" : bd.toPlainString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    // ------------------------------------------------------- expression parser

    /** Evaluates + - * / % ( ) and unary minus with correct precedence. */
    private static class Parser {
        private final String s;
        private int p = 0;

        Parser(String s) { this.s = s; }

        double parse() {
            double v = expr();
            if (p < s.length()) throw new IllegalArgumentException("Unexpected '" + s.charAt(p) + "'");
            return v;
        }

        private double expr() {
            double v = term();
            while (p < s.length()) {
                char c = s.charAt(p);
                if (c == '+') { p++; v += term(); }
                else if (c == '-') { p++; v -= term(); }
                else break;
            }
            return v;
        }

        private double term() {
            double v = factor();
            while (p < s.length()) {
                char c = s.charAt(p);
                if (c == '*') { p++; v *= factor(); }
                else if (c == '/') {
                    p++;
                    double d = factor();
                    if (d == 0) throw new ArithmeticException("Cannot divide by zero");
                    v /= d;
                } else if (c == '%') {
                    p++;
                    double d = factor();
                    if (d == 0) throw new ArithmeticException("Cannot take modulo by zero");
                    v %= d;
                } else break;
            }
            return v;
        }

        private double factor() {
            if (p >= s.length()) throw new IllegalArgumentException("Incomplete expression");
            char c = s.charAt(p);
            if (c == '-') { p++; return -factor(); }
            if (c == '+') { p++; return factor(); }
            if (c == '(') {
                p++;
                double v = expr();
                if (p >= s.length() || s.charAt(p) != ')') throw new IllegalArgumentException("Missing )");
                p++;
                return v;
            }
            int start = p;
            while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) p++;
            if (start == p) throw new IllegalArgumentException("Unexpected '" + c + "'");
            try {
                return Double.parseDouble(s.substring(start, p));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number");
            }
        }
    }
}
