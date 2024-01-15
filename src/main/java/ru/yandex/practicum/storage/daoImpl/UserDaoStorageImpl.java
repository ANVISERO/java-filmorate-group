package ru.yandex.practicum.storage.daoImpl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.exception.EntityNotFoundException;
import ru.yandex.practicum.model.User;
import ru.yandex.practicum.storage.api.UserStorage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Slf4j
@Repository
@AllArgsConstructor
public class UserDaoStorageImpl implements UserStorage {

    private final JdbcTemplate jdbcTemplate;

    private User mapToUser(ResultSet rs, int rowNum) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("user_id"));
        user.setEmail(rs.getString("email"));
        user.setLogin(rs.getString("login"));
        user.setName(rs.getString("name_user"));
        user.setBirthday(rs.getDate("birthday").toLocalDate());
        return user;
    }


    private Map<String, Object> userToMap(User user) {
        Map<String, Object> userAttributes = new HashMap<>();
        userAttributes.put("email", user.getEmail());
        userAttributes.put("login", user.getLogin());
        userAttributes.put("name_user", user.getName());
        userAttributes.put("birthday", user.getBirthday());
        return userAttributes;
    }

    @Override
    public List<User> getUsers() {
        String query = "SELECT user_id, email, login, name_user, birthday FROM User_Filmorate";
        log.debug("All users returned from DB");
        return jdbcTemplate.query(query, this::mapToUser);
    }

    @Override
    public User addUser(User user) {
        SimpleJdbcInsert simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("User_Filmorate")
                .usingGeneratedKeyColumns("user_id");
        Number key = simpleJdbcInsert.executeAndReturnKey(userToMap(user));
        user.setId((Integer) key);
        log.debug("User with ID {} saved.", user.getId());
        return user;
    }

    @Override
    public Optional<User> getUserById(Integer id) {
        String query = "SELECT user_id, email, login, name_user, birthday FROM User_Filmorate WHERE user_id=?";
        return Optional.ofNullable(jdbcTemplate.queryForObject(query, this::mapToUser, id));
    }

    @Override
    public User updateUser(User user) {
        String query = "UPDATE User_Filmorate SET email=?, login=?, name_user=?, birthday=? WHERE user_id=?";
        int userId = user.getId();
        int updateResult = jdbcTemplate.update(query,
                user.getEmail(),
                user.getLogin(),
                user.getName(),
                user.getBirthday(),
                userId);
        if (updateResult > 0) {
            log.debug("User with ID {} has been updated.", userId);
        } else {
            throw new EntityNotFoundException("User not founded for update by ID=" + userId);
        }
        return user;
    }

    @Override
    public void deleteUserById(Integer id) {
        isUserExist(id);
        try {
            // Удаление события по id пользователя
            final String sqlFeedDeleteByUserId = "DELETE FROM Event_Feed WHERE user_id=?";
            int resultFeedDelete = jdbcTemplate.update(sqlFeedDeleteByUserId, id);

            // Удаление друзей по id пользователя
            final String sqlFriendDeleteByUserId = "DELETE FROM Friendship WHERE user_id=?";
            int resultFriendDeleteByUserId = jdbcTemplate.update(sqlFriendDeleteByUserId, id);

            // Удаление друзей по id друга
            final String sqlFriendDeleteByFriendId = "DELETE FROM Friendship WHERE friend_id=?";
            int resultFriendDeleteByFriendId = jdbcTemplate.update(sqlFriendDeleteByFriendId, id);

            // Удаление самого пользователя
            final String sqlDeleteUserById = "DELETE FROM User_Filmorate WHERE user_id=?";
            int resultUserDelete = jdbcTemplate.update(sqlDeleteUserById, id);

            if (resultFeedDelete > 0 || resultFriendDeleteByUserId > 0 || resultFriendDeleteByFriendId > 0 ||
                    resultUserDelete > 0) {
                log.debug("Пользователь и связанные с ним данные успешно удалены");
            } else {
                throw new EntityNotFoundException("Пользователь не существует");
            }
        } catch (RuntimeException e) {
            log.debug("Вы отправили неверный идентификатор пользователя для запроса DELETE: {}", id);
            throw new EntityNotFoundException("Вы отправили неверный идентификатор пользователя для запроса DELETE: " + id);
        }
    }

    @Override
    public List<User> searchForUserFriends(int id) {
        String userExistQuery = "SELECT COUNT(user_id) FROM User_Filmorate WHERE user_id = ?";
        Integer userCount = jdbcTemplate.queryForObject(userExistQuery, Integer.class, id);
        if (userCount == null || userCount == 0) {
            log.debug("User with ID {} does not exist", id);
            throw new EntityNotFoundException("User not detected by ID=" + id);
        } else {
            String query = "SELECT uf.user_id, uf.email, uf.login, uf.name_user, uf.birthday " +
                    "FROM User_Filmorate uf " +
                    "JOIN Friendship f ON uf.user_id = f.friend_id " +
                    "WHERE f.user_id = ?";
            log.debug("All friends of user by ID {} returned from DB", id);
            return jdbcTemplate.query(query, this::mapToUser, id);
        }
    }

    @Override
    public void removeFriend(int userId, int friendId) {
        String query = "DELETE FROM Friendship WHERE user_id=? AND friend_id=?";
        int deleteResult = jdbcTemplate.update(query, userId, friendId);
        if (deleteResult > 0) {
            log.info("User with ID {} has been removed from friends of user by ID {}.", userId, friendId);
        } else {
            log.info("Users are not friends");
        }
    }

    @Override
    public void addFriend(int userId, int friendId) {
        if (userId <= 0 || friendId <= 0) {
            throw new EntityNotFoundException("Users with same id not exists");
        }
        String query = "INSERT INTO Friendship (user_id, friend_id) " +
                "SELECT ?, ? " +
                "WHERE NOT EXISTS ( " +
                "SELECT 1 FROM Friendship " +
                "WHERE user_id = ? AND friend_id = ?)";
        int insertResult = jdbcTemplate.update(query, userId, friendId, userId, friendId);
        if (insertResult > 0) {
            log.debug("User with ID {} has been added in friends of user by ID {}.", friendId, userId);
        }
    }

    @Override
    public List<User> searchForSameFriends(int userId, int friendId) {
        List<User> commonFriends = new ArrayList<>();
        String query = "SELECT u.user_id, u.email, u.login, u.name_user, u.birthday FROM Friendship f1 " +
                "INNER JOIN Friendship f2 ON f1.friend_id = f2.friend_id " +
                "INNER JOIN User_Filmorate u ON f1.friend_id = u.user_id " +
                "WHERE f1.user_id = ? AND f2.user_id = ? AND f1.friend_id = f2.friend_id";
        SqlRowSet sqlRowSet = jdbcTemplate.queryForRowSet(query, userId, friendId);
        while (sqlRowSet.next()) {
            int id = sqlRowSet.getInt("user_id");
            commonFriends.add(getUserById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Common friend not exist in DB with ID=" + id)));
        }
        return commonFriends;
    }

    public void isUserExist(Integer userId) {
        String sql = "SELECT user_id FROM User_Filmorate WHERE user_id = ?";
        if (!jdbcTemplate.queryForRowSet(sql, userId).next()) {
            log.warn("User with id = {} was not found", userId);
            throw new EntityNotFoundException(String.format("User with id = %d was not found", userId));
        }
    }
}