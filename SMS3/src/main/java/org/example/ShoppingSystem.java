package org.example;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.List;
import java.util.ArrayList;


class SQLiteConnector {
    public static Connection connect(String dbName) {
        Connection connection = null;
        try {
            // 加载 SQLite JDBC 驱动
            Class.forName("org.sqlite.JDBC");

            // 连接到SQLite数据库
            String url = "jdbc:sqlite:" + dbName + ".db";

            connection = DriverManager.getConnection(url);
            //System.out.println("连接到数据库成功");
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
        }
        return connection;
    }
}


class User {

    private String userID;
    private String username;
    private String password;
    private String phoneNumber;
    private String email;
    private String userLevel;
    private double totalSpent;
    private Date registrationDate;

    private int lockState;//锁定状态

    private static SQLiteConnector userConnection;

    public User(String userID, String username, String password, String phoneNumber, String email, String userLevel, double totalSpent, Date registrationDate, int lockState) {
        this.userID = userID;
        this.username = username;
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.userLevel = userLevel;
        this.totalSpent = totalSpent;
        this.registrationDate = registrationDate;
        this.lockState = lockState;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserLevel() {
        return userLevel;
    }

    public void setUserLevel(String userLevel) {
        this.userLevel = userLevel;
    }

    public double getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(double totalSpent) {
        this.totalSpent = totalSpent;
    }

    public Date getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(Date registrationDate) {
        this.registrationDate = registrationDate;
    }

    public int getLockState() {
        return lockState;
    }

    public void setLockState(int lockState) {
        this.lockState = lockState;
    }

    public void addProduct(Product product, int count) {
        Connection connection = SQLiteConnector.connect(username + "_cart");
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + username + "_cart (\n"
                + "productID TEXT PRIMARY KEY, "
                + "productName TEXT, "
                + "manufacturer TEXT, "
                + "productionDate TEXT, "
                + "model TEXT, "
                + "retailPrice REAL, "
                + "purchaseQuantity INTEGER)";

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableSQL);

            String insertProductSQL = "INSERT INTO " + username + "_cart (productID, productName, manufacturer, productionDate, model, retailPrice, purchaseQuantity) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement preparedStatement = connection.prepareStatement(insertProductSQL);
            preparedStatement.setString(1, product.getProductID());
            preparedStatement.setString(2, product.getName());
            preparedStatement.setString(3, product.getManufacturer());
            preparedStatement.setString(4, formatDate(product.getProductionDate()));
            preparedStatement.setString(5, product.getModel());
            preparedStatement.setDouble(6, product.getRetailPrice());
            preparedStatement.setInt(7, count);
            preparedStatement.executeUpdate();

            System.out.println("商品已添加到购物车。");
        } catch (SQLException e) {
            System.out.println("无法保存购物车内容到数据库。");
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.out.println("无法关闭数据库连接。");
            }
        }
    }


    public void removeProduct(Product product) {
        int removedQuantity = 0;

        if (product != null) {
            try {
                Connection connection = SQLiteConnector.connect(username + "_cart");

                String sql = "DELETE FROM " + username + "_cart WHERE productID = ?";
                PreparedStatement preparedStatement = connection.prepareStatement(sql);
                preparedStatement.setString(1, product.getProductID());

                int rowsAffected = preparedStatement.executeUpdate();

                if (rowsAffected > 0) {
                    System.out.println("商品已从购物车移除。");
                } else {
                    System.out.println("购物车中不存在该商品。");
                }

                preparedStatement.close();
                connection.close();
            } catch (SQLException e) {
                System.out.println("无法移除购物车中的商品：" + e.getMessage());
            }
        } else {
            System.out.println("商品不存在。");
        }
    }

    public int getPurchaseQuantity(Product product) {
        int purchaseQuantity = 0;

        try (Connection connection = SQLiteConnector.connect(username + "_cart")) {
            String sql = "SELECT purchaseQuantity FROM " + username + "_cart WHERE productID = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, product.getProductID());

                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    purchaseQuantity = resultSet.getInt("purchaseQuantity");
                }

                resultSet.close();
            }
        } catch (SQLException e) {
            System.out.println("无法获取移除的购买数量：" + e.getMessage());
        }

        return purchaseQuantity;
    }


    public void modifyProduct(Product product, Product newProduct, int count) {
        removeProduct(product);
        addProduct(newProduct, count);
    }

    public void checkout() {
        double total = 0.0;
        List<Product> cartContents = readCartFromDatabase();

        if (cartContents.isEmpty()) {
            System.out.println("购物车为空，无法结账。");
            return;
        }

        try (Connection connection = SQLiteConnector.connect("shopping_history")) {
            // 创建shopping_history表
            try (Statement statement = connection.createStatement()) {
                String createTableSQL = "CREATE TABLE IF NOT EXISTS shopping_history (\n"
                        + "id INTEGER PRIMARY KEY,\n"
                        + "username TEXT,\n"
                        + "purchase_time TEXT,\n"
                        + "product_info TEXT\n"
                        + ");";
                statement.executeUpdate(createTableSQL);
            }

            String purchaseTime = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());

            try (PreparedStatement insertStatement = connection.prepareStatement(
                    "INSERT INTO shopping_history (username, purchase_time, product_info) VALUES (?, ?, ?)")) {
                insertStatement.setString(1, username);
                insertStatement.setString(2, purchaseTime);

                StringBuilder productsInfo = new StringBuilder();
                for (Product product : cartContents) {
                    String productInfo = "商品ID：" + product.getProductID() +
                            "，商品名：" + product.getName() +
                            "，零售价格：￥" + product.getRetailPrice() +
                            "，购买数量：" + getPurchaseQuantity(product);
                    productsInfo.append(productInfo).append("\n");

                    total += product.getRetailPrice() * getPurchaseQuantity(product);
                }

                insertStatement.setString(3, productsInfo.toString());
                insertStatement.executeUpdate();

                setTotalSpent(totalSpent + total);
                System.out.println("结账成功！总计金额：￥" + total);

                clearCartInDatabase();
            }
        } catch (SQLException e) {
            System.out.println("无法保存购物历史记录。");
        }
    }


    private List<Product> readCartFromDatabase() {
        List<Product> cartContents = new ArrayList<>();

        try (Connection connection = SQLiteConnector.connect(username + "_cart")) {
            String selectCartSQL = "SELECT * FROM " + username + "_cart";
            try (PreparedStatement preparedStatement = connection.prepareStatement(selectCartSQL)) {
                ResultSet resultSet = preparedStatement.executeQuery();

                while (resultSet.next()) {
                    String productID = resultSet.getString("productID");
                    String productName = resultSet.getString("productName");
                    String manufacturer = resultSet.getString("manufacturer");
                    String productionDateStr = resultSet.getString("productionDate");
                    Date productionDate = parseDate(productionDateStr);
                    String model = resultSet.getString("model");
                    double retailPrice = resultSet.getDouble("retailPrice");
                    int purchaseQuantity = resultSet.getInt("purchaseQuantity");

                    Product product = new Product(productID, productName, manufacturer, productionDate, model, 0, retailPrice, 0);
                    cartContents.add(product);
                }
            }
        } catch (SQLException e) {
            System.out.println("无法读取购物车内容。");
        }

        return cartContents;
    }

    private Date parseDate(String dateString) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return dateFormat.parse(dateString);
        } catch (ParseException e) {
            System.out.println("无法解析日期：" + e.getMessage());
            return null;
        }
    }

    private void clearCartInDatabase() {
        try (Connection connection = SQLiteConnector.connect(username + "_cart")) {
            String deleteCartSQL = "DELETE FROM " + username + "_cart";
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(deleteCartSQL);
            }
        } catch (SQLException e) {
            System.out.println("无法清空购物车内容。");
        }
    }

    public void viewShoppingHistory() {
        System.out.println("购物历史记录：");
        Connection connection = SQLiteConnector.connect("shopping_history");

        if (connection == null) {
            System.out.println("无法连接到购物历史数据库。");
            return;
        }

        try {
            String selectHistorySQL = "SELECT * FROM shopping_history WHERE username = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(selectHistorySQL)) {
                preparedStatement.setString(1, username);
                ResultSet resultSet = preparedStatement.executeQuery();

                boolean displayRecord = false;
                while (resultSet.next()) {
                    String purchaseTime = resultSet.getString("purchase_time");
                    String productInfo = resultSet.getString("product_info");

                    displayRecord = true;
                    System.out.println("购买时间：" + purchaseTime);
                    System.out.println(productInfo);
                }

                if (!displayRecord) {
                    System.out.println("购物历史记录为空！");
                }
            }
        } catch (SQLException e) {
            System.out.println("无法读取购物历史记录。");
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                System.out.println("无法关闭数据库连接。");
            }
        }
    }

    // 将日期格式化为字符串
    private String formatDate(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.format(date);
    }
}

class Product {
    private String productID;
    private String name;
    private String manufacturer;
    private Date productionDate;
    private String model;
    private double purchasePrice;
    private double retailPrice;
    private int quantity;

    public Product(String productID, String name, String manufacturer, Date productionDate,
                   String model, double purchasePrice, double retailPrice, int quantity) {
        this.productID = productID;
        this.name = name;
        this.manufacturer = manufacturer;
        this.productionDate = productionDate;
        this.model = model;
        this.purchasePrice = purchasePrice;
        this.retailPrice = retailPrice;
        this.quantity = quantity;
    }

    public String getProductID() {
        return productID;
    }

    public void setProductID(String productID) {
        this.productID = productID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public Date getProductionDate() {
        return productionDate;
    }

    public void setProductionDate(Date productionDate) {
        this.productionDate = productionDate;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(double purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public double getRetailPrice() {
        return retailPrice;
    }

    public void setRetailPrice(double retailPrice) {
        this.retailPrice = retailPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}

class UserManager {
    private List<User> users;
    private User currentUser;

    public UserManager() {
        users = new ArrayList<>();
        currentUser = null;
    }

    public List<User> getUsers() {
        return users;
    }

    public void registerUser(String userID, String username, String password, String phoneNumber, String email, String userLevel, double totalSpent, Date registrationDate, int lockState) {
        if (username.length() < 5) {
            System.out.println("用户名长度至少为5个字符。");
            return;
        }

        if (password.length() <= 8 || !isStrongPassword(password)) {
            System.out.println("密码长度必须大于8个字符，并包含大小写字母、数字和标点符号的组合。");
            return;
        }

        if (isUsernameTaken(username)) {
            System.out.println("用户名已被使用，请选择其他用户名。");
            return;
        }

        User user = new User(userID, username, password, phoneNumber, email, userLevel, totalSpent, registrationDate, lockState);
        users.add(user);
        System.out.println("注册成功！");
        saveUsersToDatabase();
    }

    private boolean isStrongPassword(String password) {
        // 密码长度必须大于8个字符
        if (password.length() <= 8) {
            return false;
        }

        // 密码必须包含至少一个大写字母、一个小写字母、一个数字和一个特殊字符
        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        boolean hasSpecialChar = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUppercase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowercase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                // 这里可以根据你的定义添加更多特殊字符的判断
                hasSpecialChar = true;
            }
        }

        return hasUppercase && hasLowercase && hasDigit && hasSpecialChar;
    }

    private boolean isUsernameTaken(String username) {
        for (User user : users) {
            if (user.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public boolean logInUser(String username, String password) {
        for (User user : users) {
            if (user.getUsername().equals(username)) {
                if (user.getLockState() == 5) {
                    System.out.println("用户已锁定，请联系管理员重置密码！");
                    return false;
                } else if (user.getPassword().equals(password)) {
                    currentUser = user;
                    user.setLockState(0);
                    System.out.println("登录成功！");
                    return true;
                } else {
                    user.setLockState(user.getLockState() + 1);
                }
            }
        }
        System.out.println("用户名或密码错误！");
        return false;
    }

    public User getCurrentUser() {
        return currentUser;
    }


    public void logOutUser() {
        currentUser = null;
        System.out.println("已退出登录。");
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public void changePassword(String currentPassword, String newPassword) {
        if (currentUser != null && currentUser.getPassword().equals(currentPassword)) {
            currentUser.setPassword(newPassword);
            System.out.println("密码修改成功！");
            saveUsersToDatabase();
        } else {
            System.out.println("当前密码不正确！");
        }
    }

    public void resetPassword(String userName) {
        for (User user : users) {
            if (user.getUsername().equals(userName)) {
                String phoneNumber = user.getPhoneNumber();
                String newPass = phoneNumber.substring(phoneNumber.length() - 6);
                user.setLockState(0);
                user.setPassword(newPass);
                System.out.println("密码重置成功！新密码为：" + newPass);
                saveUsersToDatabase();
                return;
            }
        }
        System.out.println("用户名不存在！");
    }

    public void saveUsersToDatabase() {
        try (Connection connection = SQLiteConnector.connect("user_database")) {
            String createTableSQL = "CREATE TABLE IF NOT EXISTS users (\n"
                    + "userID TEXT PRIMARY KEY, "
                    + "username TEXT, "
                    + "password TEXT, "
                    + "phoneNumber TEXT, "
                    + "email TEXT, "
                    + "userLevel TEXT, "
                    + "totalSpent REAL, "
                    + "registrationDate TEXT, "
                    + "lockState INTEGER)";

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createTableSQL);

                String deleteAllUsersSQL = "DELETE FROM users"; // 清空表中的数据
                statement.executeUpdate(deleteAllUsersSQL);

                String insertUserSQL = "INSERT INTO users (userID, username, password, phoneNumber, email, userLevel, totalSpent, registrationDate, lockState) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                PreparedStatement preparedStatement = connection.prepareStatement(insertUserSQL);
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                for (User user : users) {
                    preparedStatement.setString(1, user.getUserID());
                    preparedStatement.setString(2, user.getUsername());
                    preparedStatement.setString(3, user.getPassword());
                    preparedStatement.setString(4, user.getPhoneNumber());
                    preparedStatement.setString(5, user.getEmail());
                    preparedStatement.setString(6, user.getUserLevel());
                    preparedStatement.setDouble(7, user.getTotalSpent());
                    preparedStatement.setString(8, dateFormat.format(user.getRegistrationDate()));
                    preparedStatement.setInt(9, user.getLockState());
                    preparedStatement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("无法保存用户信息到数据库：" + e.getMessage());
        }
    }

    public void loadUsersFromDatabase() {
        try (Connection connection = SQLiteConnector.connect("user_database")) {
            String selectUsersSQL = "SELECT * FROM users";

            try (PreparedStatement preparedStatement = connection.prepareStatement(selectUsersSQL)) {
                ResultSet resultSet = preparedStatement.executeQuery();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                while (resultSet.next()) {
                    String userID = resultSet.getString("userID");
                    String username = resultSet.getString("username");
                    String password = resultSet.getString("password");
                    String phoneNumber = resultSet.getString("phoneNumber");
                    String email = resultSet.getString("email");
                    String userLevel = resultSet.getString("userLevel");
                    double totalSpent = resultSet.getDouble("totalSpent");
                    Date registrationDate = dateFormat.parse(resultSet.getString("registrationDate"));
                    int lockState = resultSet.getInt("lockState");

                    User user = new User(userID, username, password, phoneNumber, email, userLevel, totalSpent, registrationDate, lockState);
                    users.add(user);
                }
            }
        } catch (SQLException | ParseException e) {
            System.out.println("无法读取用户信息数据库：" + e.getMessage());
        }
    }

    public void initialize() {
        loadUsersFromDatabase();
    }
}

class ProductManager {
    private List<Product> products;

    public List<Product> getProducts() {
        return products;
    }

    public ProductManager() {
        products = new ArrayList<>();
    }

    public void addProduct(String productID, String name, String manufacturer, Date productionDate,
                           String model, double purchasePrice, double retailPrice, int quantity) {
        Product product = new Product(productID, name, manufacturer, productionDate, model, purchasePrice, retailPrice, quantity);
        products.add(product);
        System.out.println("商品添加成功！");
        saveProductsToDatabase();
    }

    public void removeProduct(String name) {
        Scanner scanner = new Scanner(System.in);
        for (Product product : products) {
            if (product.getName().equals(name)) {
                System.out.print("删除后无法恢复，请用户确认是否继续删除操作（输入'Y'确认，其他任意键取消）：");
                if (scanner.nextLine().equals("Y")) {
                    products.remove(product);
                    System.out.println("商品已移除。");
                    saveProductsToDatabase();
                    return;
                } else {
                    System.out.println("操作取消");
                    return;
                }

            }
        }
        System.out.println("商品不存在。");
    }

    public void modifyProduct(String name, int choice) throws ParseException {
        Scanner scanner = new Scanner(System.in);
        Product targetProduct = null;
        for (Product product : products) {
            if (product.getName().equals(name)) {
                System.out.println("商品编号：" + product.getProductID() + "，商品名称：" + product.getName() + "，生产厂家：" + product.getManufacturer() + "，生产日期：" + dateToString(product.getProductionDate()) + "，型号：" + product.getModel() + "，进货价：￥" + product.getPurchasePrice() + "，零售价格：￥：" + product.getRetailPrice() + "，库存数量：" + product.getQuantity());
                targetProduct = product;
            }
        }
        if (targetProduct == null) {
            System.out.println("商品不存在。");
            return;
        }
        switch (choice) {
            case 1:
                System.out.print("请输入新的商品编号：");
                targetProduct.setProductID(scanner.nextLine());
                System.out.println("商品编号已修改。");
                break;
            case 2:
                System.out.print("请输入新的商品名称：");
                targetProduct.setName(scanner.nextLine());
                System.out.println("商品名称已修改。");

                break;
            case 3:
                System.out.print("请输入新的生产厂家：");
                targetProduct.setManufacturer(scanner.nextLine());
                System.out.println("生产厂家已修改。");
                break;
            case 4:
                System.out.print("请输入新的生产日期：");
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); // 根据实际日期格式修改
                targetProduct.setProductionDate(dateFormat.parse(scanner.nextLine()));
                System.out.println("生产日期已修改。");
                break;
            case 5:
                System.out.print("请输入新的商品型号：");
                targetProduct.setModel(scanner.nextLine());
                System.out.println("商品型号已修改。");
                break;
            case 6:
                System.out.print("请输入新的进货价：");
                targetProduct.setPurchasePrice(scanner.nextDouble());
                System.out.println("进货价已修改。");

                break;
            case 7:
                System.out.print("请输入新的零售价格：");
                targetProduct.setRetailPrice(scanner.nextDouble());
                System.out.println("零售价格已修改。");
                break;
            case 8:
                System.out.print("请输入新的商品数量：");
                targetProduct.setQuantity(scanner.nextInt());
                System.out.println("商品数量已修改。");
                break;
            case 9:
                return;
            default:
                System.out.println("你输入的序号有误！");
        }
        saveProductsToDatabase();
    }

    public void viewAllProducts() {
        System.out.println("所有商品信息：");
        for (Product product : products) {
            System.out.println("商品编号：" + product.getProductID() + "，商品名称：" + product.getName() + "，生产厂家：" + product.getManufacturer() + "，生产日期：" + dateToString(product.getProductionDate()) + "，型号：" + product.getModel() + "，进货价：￥" + product.getPurchasePrice() + "，零售价格：￥" + product.getRetailPrice() + "，库存数量：" + product.getQuantity());
        }
    }

    public void searchProduct(int choice) {
        Scanner scanner = new Scanner(System.in);
        boolean flag = false;
        switch (choice) {
            case 1:
                System.out.print("请输入商品名称：");
                String name = scanner.nextLine();
                System.out.println("商品信息：");
                for (Product product : products) {
                    if (product.getName().equals(name)) {
                        System.out.println("商品编号：" + product.getProductID() + "，商品名称：" + product.getName() + "，生产厂家：" + product.getManufacturer() + "，生产日期：" + dateToString(product.getProductionDate()) + "，型号：" + product.getModel() + "，进货价：￥" + product.getPurchasePrice() + "，零售价格：￥" + product.getRetailPrice() + "，库存数量：" + product.getQuantity());
                        flag = true;
                    }
                }
                break;
            case 2:
                System.out.print("请输入生产厂家：");
                String manufacturer = scanner.nextLine();
                System.out.println("商品信息：");
                for (Product product : products) {
                    if (product.getManufacturer().equals(manufacturer)) {
                        System.out.println("商品编号：" + product.getProductID() + "，商品名称：" + product.getName() + "，生产厂家：" + product.getManufacturer() + "，生产日期：" + dateToString(product.getProductionDate()) + "，型号：" + product.getModel() + "，进货价：" + product.getPurchasePrice() + "，零售价格：" + product.getRetailPrice() + "，库存数量" + product.getQuantity());
                        flag = true;
                    }
                }
                break;
            case 3:
                System.out.print("请输入价格区间下界：");
                double lowerBound = scanner.nextDouble();
                System.out.print("请输入价格区间上界：");
                double upperBound = scanner.nextDouble();
                System.out.println("商品信息：");
                for (Product product : products) {
                    if (product.getRetailPrice() >= lowerBound && product.getRetailPrice() <= upperBound) {
                        System.out.println("商品编号：" + product.getProductID() + "，商品名称：" + product.getName() + "，生产厂家：" + product.getManufacturer() + "，生产日期：" + dateToString(product.getProductionDate()) + "，型号：" + product.getModel() + "，进货价：￥" + product.getPurchasePrice() + "，零售价格：￥" + product.getRetailPrice() + "，库存数量：" + product.getQuantity());
                        flag = true;
                    }
                }
                break;
            default:
                System.out.println("你输入的序号有误！");
        }
        if (!flag) {
            System.out.println("商品不存在。");
        }
    }

    public void saveProductsToDatabase() {
        try (Connection connection = SQLiteConnector.connect("product_database")) {
            String createTableSQL = "CREATE TABLE IF NOT EXISTS products (\n"
                    + "productID TEXT PRIMARY KEY, "
                    + "name TEXT, "
                    + "manufacturer TEXT, "
                    + "productionDate TEXT, "
                    + "model TEXT, "
                    + "purchasePrice REAL, "
                    + "retailPrice REAL, "
                    + "quantity INTEGER)";

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createTableSQL);

                String deleteAllProductsSQL = "DELETE FROM products"; // 清空表中的数据
                statement.executeUpdate(deleteAllProductsSQL);

                String insertProductSQL = "INSERT INTO products (productID, name, manufacturer, productionDate, model, purchasePrice, retailPrice, quantity) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                PreparedStatement preparedStatement = connection.prepareStatement(insertProductSQL);
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

                for (Product product : products) {
                    preparedStatement.setString(1, product.getProductID());
                    preparedStatement.setString(2, product.getName());
                    preparedStatement.setString(3, product.getManufacturer());
                    preparedStatement.setString(4, dateFormat.format(product.getProductionDate()));
                    preparedStatement.setString(5, product.getModel());
                    preparedStatement.setDouble(6, product.getPurchasePrice());
                    preparedStatement.setDouble(7, product.getRetailPrice());
                    preparedStatement.setInt(8, product.getQuantity());
                    preparedStatement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("无法保存商品信息到数据库：" + e.getMessage());
        }
    }

    public void loadProductsFromDatabase() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        try (Connection connection = SQLiteConnector.connect("product_database")) {
            String selectProductsSQL = "SELECT * FROM products";

            try (PreparedStatement preparedStatement = connection.prepareStatement(selectProductsSQL)) {
                ResultSet resultSet = preparedStatement.executeQuery();

                while (resultSet.next()) {
                    String productID = resultSet.getString("productID");
                    String name = resultSet.getString("name");
                    String manufacturer = resultSet.getString("manufacturer");
                    Date productionDate = dateFormat.parse(resultSet.getString("productionDate"));
                    String model = resultSet.getString("model");
                    double purchasePrice = resultSet.getDouble("purchasePrice");
                    double retailPrice = resultSet.getDouble("retailPrice");
                    int quantity = resultSet.getInt("quantity");
                    Product product = new Product(productID, name, manufacturer, productionDate, model, purchasePrice, retailPrice, quantity);
                    products.add(product);
                }
            }
        } catch (SQLException | ParseException e) {
            System.out.println("无法读取商品信息数据库：" + e.getMessage());
        }
    }

    private static String dateToString(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); // 根据实际日期格式修改
        String formattedDate = dateFormat.format(date);
        return formattedDate;
    }

    public void initialize() {
        loadProductsFromDatabase();
    }
}

class Administrator {
    private String adminPassword = "ynuinfo#777";

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }
}

public class ShoppingSystem {
    private static UserManager userManager;
    private static ProductManager productManager;
    private static Administrator administrator = new Administrator();


    public static void main(String[] args) throws ParseException {
        userManager = new UserManager();
        productManager = new ProductManager();

        Scanner scanner = new Scanner(System.in);

        userManager.initialize();
        productManager.initialize();

        while (true) {
            System.out.println("请选择用户界面或管理员界面：");
            System.out.println("1. 用户界面");
            System.out.println("2. 管理员界面");
            System.out.println("3. 退出系统");
            System.out.print("请输入选择的序号：");
            int choice = scanner.nextInt();
            scanner.nextLine(); // 读取换行符

            switch (choice) {
                case 1:
                    userInterface(scanner);
                    break;
                case 2:
                    adminInterface(scanner);
                    break;
                case 3:
                    System.out.println("感谢使用！");
                    return;
                default:
                    System.out.println("输入有误，请重新选择。");
            }
        }
    }

    private static void userInterface(Scanner scanner) {
        while (true) {
            System.out.println("-------- 用户界面 --------");
            System.out.println("1. 登录");
            System.out.println("2. 注册");
            System.out.println("3. 返回");
            System.out.print("请输入选择的序号：");
            int choice = scanner.nextInt();
            scanner.nextLine(); // 读取换行符

            switch (choice) {
                case 1:
                    login(scanner);
                    break;
                case 2:
                    register(scanner);
                    break;
                case 3:
                    return;
                default:
                    System.out.println("输入有误，请重新选择。");
            }
        }
    }

    private static void adminInterface(Scanner scanner) throws ParseException {
        while (true) {
            System.out.println("-------- 管理员界面 --------");
            System.out.println("1. 登录");
            System.out.println("2. 返回");
            System.out.print("请输入选择的序号：");
            int choice = scanner.nextInt();
            scanner.nextLine(); // 读取换行符

            switch (choice) {
                case 1:
                    adminLogin(scanner);
                    break;
                case 2:
                    return;
                default:
                    System.out.println("输入有误，请重新选择。");
            }
        }
    }

    private static void login(Scanner scanner) {
        System.out.print("请输入用户名：");
        String username = scanner.nextLine();
        System.out.print("请输入密码：");
        String password = scanner.nextLine();
        userManager.logInUser(username, password);

        if (userManager.isLoggedIn()) {
            userLoggedInInterface(scanner);
        }
    }

    private static void register(Scanner scanner) {
        // 生成十位数的随机用户ID
        String userID = generateUniqueUserID();

        System.out.print("请输入用户名：");
        String username = scanner.nextLine();
        System.out.print("请输入密码：");
        String password = scanner.nextLine();
        System.out.print("请输入电话号码：");
        String phoneNumber = scanner.nextLine();
        System.out.print("请输入邮箱号：");
        String email = scanner.nextLine();
        Date registeDate = new Date();
        userManager.registerUser(userID, username, password, phoneNumber, email, "铜牌用户", 0, registeDate, 0);
    }

    // 生成不重复的用户ID
    private static String generateUniqueUserID() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }


    private static void userLoggedInInterface(Scanner scanner) {
        User currentUser = userManager.getCurrentUser();
        if (currentUser == null) {
            System.out.println("用户未登录！");
            return;
        }

        while (true) {
            System.out.println("-------- 用户界面 --------");
            System.out.println("1. 密码管理");
            System.out.println("2. 购物");
            System.out.println("3. 退出登录");
            System.out.print("请输入选择的序号：");
            int choice = scanner.nextInt();
            scanner.nextLine(); // 读取换行符

            switch (choice) {
                case 1:
                    passwordManagement(scanner);
                    break;
                case 2:
                    shopping(scanner, currentUser);
                    break;
                case 3:
                    userManager.logOutUser();
                    return;
                default:
                    System.out.println("输入有误，请重新选择。");
            }
        }
    }

    private static void passwordManagement(Scanner scanner) {
        while (true) {
            System.out.println("-------- 密码管理 --------");
            System.out.println("1. 修改密码");
            System.out.println("2. 返回");
            System.out.print("请输入选择的序号：");
            int choice = scanner.nextInt();
            scanner.nextLine(); // 读取换行符

            switch (choice) {
                case 1:
                    changePassword(scanner);
                    break;
                case 2:
                    return;
                default:
                    System.out.println("输入有误，请重新选择。");
            }
        }
    }

    private static void changePassword(Scanner scanner) {
        System.out.print("请输入当前密码：");
        String currentPassword = scanner.nextLine();
        System.out.print("请输入新密码：");
        String newPassword = scanner.nextLine();
        if (newPassword.length() <= 8 || !isStrongPassword(newPassword)) {
            System.out.println("密码长度必须大于8个字符，并包含大小写字母、数字和标点符号的组合。");
            return;
        }

        userManager.changePassword(currentPassword, newPassword);
    }

    private static boolean isStrongPassword(String password) {
        // 密码长度必须大于8个字符
        if (password.length() <= 8) {
            return false;
        }

        // 密码必须包含至少一个大写字母、一个小写字母、一个数字和一个特殊字符
        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        boolean hasSpecialChar = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUppercase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowercase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                // 这里可以根据你的定义添加更多特殊字符的判断
                hasSpecialChar = true;
            }
        }

        return hasUppercase && hasLowercase && hasDigit && hasSpecialChar;
    }

    private static void shopping(Scanner scanner, User user) {
        while (true) {
            System.out.println("-------- 购物 --------");
            System.out.println("1. 将商品加入购物车");
            System.out.println("2. 从购物车中移除商品");
            System.out.println("3. 修改购物车中的商品");
            System.out.println("4. 模拟结账");
            System.out.println("5. 查看购物历史记录");
            System.out.println("6. 返回");
            System.out.print("请输入选择的序号：");
            int choice = scanner.nextInt();
            scanner.nextLine(); // 读取换行符

            switch (choice) {
                case 1:
                    addToCart(scanner, user);
                    break;
                case 2:
                    removeFromCart(scanner, user);
                    break;
                case 3:
                    modifyCart(scanner, user);
                    break;
                case 4:
                    user.checkout();
                    break;
                case 5:
                    user.viewShoppingHistory();
                    break;
                case 6:
                    return;
                default:
                    System.out.println("输入有误，请重新选择。");
            }
        }
    }

    private static void addToCart(Scanner scanner, User user) {
        System.out.print("请输入商品名：");
        String name = scanner.nextLine();
        Product product = getProductByName(name);
        if (product != null) {
            System.out.print("请输入购买数量：");
            int count = scanner.nextInt();
            if (count > 0) {
                if (product.getQuantity() < count) {
                    System.out.println("库存不足，添加失败！");
                    return;
                }
                user.addProduct(product, count);
                product.setQuantity(product.getQuantity() - count);
            } else {
                System.out.println("输入数量有误！");
            }
        } else {
            System.out.println("商品不存在。");
        }
    }

    private static void removeFromCart(Scanner scanner, User user) {
        System.out.print("请输入商品名：");
        String name = scanner.nextLine();
        Product product = getProductByName(name);
        if (product != null) {
            System.out.print("确认移除商品？（输入'Y'确认，其他任意键取消）:");
            if (!scanner.nextLine().equals("Y")) {
                int count = user.getPurchaseQuantity(product);
                user.removeProduct(product);
                product.setQuantity(product.getQuantity() + count);
                System.out.println("商品已移出购物车。");

            } else {
                System.out.println("操作取消");
            }
        } else {
            System.out.println("商品不存在。");
        }
    }

    private static void modifyCart(Scanner scanner, User user) {
        System.out.print("请输入商品名：");
        String name = scanner.nextLine();
        Product product = getProductByName(name);
        if (product != null) {
            System.out.print("请输入需要替换的商品名：");
            String newName = scanner.nextLine();
            Product newProduct = getProductByName(newName);
            if (newProduct != null) {
                System.out.print("请输入替换商品的数量：");
                int count = scanner.nextInt();
                if (count > 0) {
                    user.modifyProduct(product, newProduct, count);
                } else {
                    System.out.println("输入数量有误！");
                }
            } else {
                System.out.println("商品不存在。");
            }
        } else {
            System.out.println("商品不存在。");
        }
    }

    private static Product getProductByName(String name) {
        for (Product product : productManager.getProducts()) {
            if (product.getName().equals(name)) {
                return product;
            }
        }
        return null;
    }

    private static void adminLogin(Scanner scanner) throws ParseException {
        System.out.print("请输入管理员密码：");
        String password = scanner.nextLine();
        if (password.equals(administrator.getAdminPassword())) {
            System.out.println("登录成功！");
            adminLoggedInInterface(scanner);
        } else {
            System.out.println("密码错误！");
        }
    }

    private static void adminLoggedInInterface(Scanner scanner) throws ParseException {
        while (true) {
            System.out.println("-------- 管理员界面 --------");
            System.out.println("1. 密码管理");
            System.out.println("2. 商品管理");
            System.out.println("3. 用户管理");
            System.out.println("4. 退出登录");
            System.out.print("请输入选择的序号：");
            int choice = scanner.nextInt();
            scanner.nextLine(); // 读取换行符

            switch (choice) {
                case 1:
                    adminPasswordManagement(scanner);
                    break;
                case 2:
                    productManagement(scanner);
                    break;
                case 3:
                    userManagement(scanner);
                    break;
                case 4:
                    return;
                default:
                    System.out.println("输入有误，请重新选择。");
            }
        }
    }

    private static void adminPasswordManagement(Scanner scanner) {
        while (true) {
            System.out.println("-------- 密码管理 --------");
            System.out.println("1. 修改管理员密码");
            System.out.println("2. 重置用户密码");
            System.out.println("3. 返回");
            System.out.print("请输入选择的序号：");
            int choice = scanner.nextInt();
            scanner.nextLine(); // 读取换行符

            switch (choice) {
                case 1:
                    changeAdminPassword(scanner);
                    break;
                case 2:
                    resetPassword(scanner);
                    break;
                case 3:
                    return;
                default:
                    System.out.println("输入有误，请重新选择。");
            }
        }
    }

    private static void changeAdminPassword(Scanner scanner) {
        System.out.print("请输入当前管理员密码：");
        String currentPassword = scanner.nextLine();
        System.out.print("请输入新密码：");
        String newPassword = scanner.nextLine();
        if (administrator.getAdminPassword().equals(currentPassword)) {
            administrator.setAdminPassword(newPassword);
            System.out.println("修改成功！");
        } else {
            System.out.println("当前管理员密码错误！");
        }

    }

    private static void resetPassword(Scanner scanner) {
        System.out.print("请输入用户名：");
        String userName = scanner.nextLine();
        userManager.resetPassword(userName);
    }

    private static void productManagement(Scanner scanner) throws ParseException {
        while (true) {
            System.out.println("-------- 商品管理 --------");
            System.out.println("1. 添加商品");
            System.out.println("2. 移除商品");
            System.out.println("3. 修改商品信息");
            System.out.println("4. 查看所有商品");
            System.out.println("5. 搜索商品");
            System.out.println("6. 返回");
            System.out.print("请输入选择的序号：");
            int choice = scanner.nextInt();
            scanner.nextLine(); // 读取换行符

            switch (choice) {
                case 1:
                    addProduct(scanner);
                    break;
                case 2:
                    removeProduct(scanner);
                    break;
                case 3:
                    modifyProduct(scanner);
                    break;
                case 4:
                    productManager.viewAllProducts();
                    break;
                case 5:
                    searchProduct(scanner);
                    break;
                case 6:
                    return;
                default:
                    System.out.println("输入有误，请重新选择。");
            }
        }
    }

    private static void addProduct(Scanner scanner) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        System.out.print("请输入商品编号：");
        String productID = scanner.nextLine();
        System.out.print("请输入商品名称：");
        String name = scanner.nextLine();
        System.out.print("请输入生产厂家：");
        String manufacture = scanner.nextLine();
        System.out.print("请输入生产日期(yyyy-MM-dd)：");
        String productionDate = scanner.nextLine();
        Date formatedDate = dateFormat.parse(productionDate);
        System.out.print("请输入型号：");
        String model = scanner.nextLine();
        System.out.print("请输入进货价：");
        double purchasePrice = scanner.nextDouble();
        scanner.nextLine(); // 读取换行符
        System.out.print("请输入零售价格：");
        double retailPrice = scanner.nextDouble();
        scanner.nextLine(); // 读取换行符
        System.out.print("请输入库存数量：");
        int quantity = scanner.nextInt();
        productManager.addProduct(productID, name, manufacture, formatedDate, model, purchasePrice, retailPrice, quantity);
    }

    private static void removeProduct(Scanner scanner) {
        System.out.print("请输入商品名：");
        String name = scanner.nextLine();
        productManager.removeProduct(name);
    }

    private static void modifyProduct(Scanner scanner) throws ParseException {
        System.out.print("请输入商品名：");
        String name = scanner.nextLine();
        System.out.println("-------- 商品修改 --------");
        System.out.println("1. 修改商品编号");
        System.out.println("2. 修改商品名称");
        System.out.println("3. 修改生产厂家");
        System.out.println("4. 修改生产日期");
        System.out.println("5. 修改型号");
        System.out.println("6. 修改进货价");
        System.out.println("7. 修改零售价格");
        System.out.println("8. 修改库存数量");
        System.out.println("9.返回");
        System.out.print("请输入选择的序号：");
        int choice = scanner.nextInt();
        scanner.nextLine(); // 读取换行符
        productManager.modifyProduct(name, choice);
    }

    private static void searchProduct(Scanner scanner) {
        System.out.println("-------- 商品查询 --------");
        System.out.println("1. 按商品名称查询");
        System.out.println("2. 按生产厂家查询");
        System.out.println("3. 按价格区间查询");
        System.out.print("请输入选择的序号：");
        int choice = scanner.nextInt();
        scanner.nextLine(); // 读取换行符
        productManager.searchProduct(choice);
    }

    private static void userManagement(Scanner scanner) {
        while (true) {
            System.out.println("-------- 用户管理 --------");
            System.out.println("1. 列出所有用户信息");
            System.out.println("2. 删除用户信息");
            System.out.println("3. 查询用户信息");
            System.out.println("4. 返回");
            System.out.print("请输入选择的序号：");
            int choice = scanner.nextInt();
            scanner.nextLine(); // 读取换行符

            switch (choice) {
                case 1:
                    listUsers();
                    break;
                case 2:
                    removeUser(scanner);
                    break;
                case 3:
                    searchUser(scanner);
                    break;
                case 4:
                    return;
                default:
                    System.out.println("输入有误，请重新选择。");
            }
        }
    }

    private static void listUsers() {
        List<User> users = userManager.getUsers();
        if (users.isEmpty()) {
            System.out.println("没有用户信息。");
        } else {
            System.out.println("所有用户信息：");
            for (User user : users) {
                System.out.println("用户编号：" + user.getUserID() + ",用户名称：" + user.getUsername() + ",用户电话：" + user.getPhoneNumber() + ",用户邮箱：" + user.getEmail() + ",用户等级：" + user.getUserLevel() + ",用户总消费：" + user.getTotalSpent() + ",注册日期：" + user.getRegistrationDate() + ",锁定状态：" + user.getLockState());
            }
        }
    }

    private static void removeUser(Scanner scanner) {
        System.out.print("请输入要删除的用户名：");
        String username = scanner.nextLine();

        for (User user : userManager.getUsers()) {
            if (user.getUsername().equals(username)) {
                System.out.print("你确定要删除客户信息吗？（输入 'Y' 确认，其他任意键取消）：");
                String confirmation = scanner.nextLine();
                if (confirmation.equalsIgnoreCase("Y")) {
                    userManager.getUsers().remove(user);
                    System.out.println("用户信息已删除。");
                    userManager.saveUsersToDatabase();
                } else {
                    System.out.println("操作取消。");
                }
                return;
            }
        }
        System.out.println("用户不存在。");
    }

    private static void searchUser(Scanner scanner) {
        System.out.println("1.按ID查询");
        System.out.println("2.按用户名查询");
        System.out.print("请输入选择的序号：");
        int key = scanner.nextInt();
        scanner.nextLine(); // 读取换行符
        switch (key) {
            case 1:
                System.out.print("请输入要查询的ID：");
                String userID = scanner.nextLine();
                for (User user : userManager.getUsers()) {
                    if (user.getUserID().equals(userID)) {
                        System.out.println("用户信息：");
                        System.out.println("用户编号：" + user.getUserID() + ",用户名称：" + user.getUsername() + ",用户电话：" + user.getPhoneNumber() + ",用户邮箱：" + user.getEmail() + ",用户等级：" + user.getUserLevel() + ",用户总消费：" + user.getTotalSpent() + ",注册日期：" + user.getRegistrationDate() + ",锁定状态：" + user.getLockState());
                        return;
                    }
                }
                System.out.println("用户不存在。");
                break;
            case 2:
                System.out.print("请输入要查询的用户名：");
                String username = scanner.nextLine();
                for (User user : userManager.getUsers()) {
                    if (user.getUsername().equals(username)) {
                        System.out.println("用户信息：");
                        System.out.println("用户编号：" + user.getUserID() + ",用户名称：" + user.getUsername() + ",用户电话：" + user.getPhoneNumber() + ",用户邮箱：" + user.getEmail() + ",用户等级：" + user.getUserLevel() + ",用户总消费：" + user.getTotalSpent() + ",注册日期：" + user.getRegistrationDate() + ",锁定状态：" + user.getLockState());
                        return;
                    }
                }
                System.out.println("用户不存在。");
                break;
            default:
                System.out.println("你输入的序号有误！");
        }
    }
}
