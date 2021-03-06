package telegrambot.commands;

import telegrambot.apimanager.ApiManager;
import telegrambot.dbmanager.DbManager;
import telegrambot.user.ProcessMode;
import telegrambot.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.invest.openapi.model.rest.BrokerAccountType;
import ru.tinkoff.invest.openapi.model.rest.UserAccount;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class CommandStart implements Command{

    private final DbManager dbManager;
    private final Long chatId;
    private final String recivedMessage;

    private final Logger logger = LoggerFactory.getLogger(CommandStart.class);

    public CommandStart(DbManager dbManager, Long chatId, String message) {
        this.dbManager = dbManager;
        this.chatId = chatId;
        this.recivedMessage = message;
    }

    @Override
    public String description() {
        return "/start - одноразовая команда для первоначальной настройки бота";
    }

    @Override
    public String execute() {
        if ("/start".equals(recivedMessage)) {
            return "<b>Шалом!</b> Я самый кошерный бот для управления портфелем Тинькофф инвестиций. " +
                    "Если вы доверите мне свой капитал, таки я стану вышим персональным менеджером. \n\n" +
                    "Для начала работы мне необходим ваш персональный " +
                    "<a href=\"https://www.tinkoff.ru/invest/settings/\">токен</a>" +
                    " Тинькофф Инвестиции. \n\n" +
                    "<i>*Отправьте его следующим сообщением</i>";
        }

        User user = dbManager.getUser(chatId);

        if (user == null) {
            if (ApiManager.checkToken(recivedMessage)) {
                dbManager.addUser(chatId, recivedMessage);
                User newUser = dbManager.getUser(chatId);
                return "Теперь нужно выбрать брокерский счёт для управления. \n"
                        + printBrokerAccountId(newUser.getToken());
            }

            return "Неверый токен";
        }

        if (ApiManager.checkBrokerAccount(user.getToken(), recivedMessage)) {
            dbManager.updateUserField(chatId, "brokerAccountId", recivedMessage);
            dbManager.updateUserField(chatId, "processMode", String.valueOf(ProcessMode.DEFAULT));
            return "Id Аккаунта установлен. Вы прошли этап основных настроек. " +
                    "Для получения списка команд отправьте /help";
        }

        return "Таки неверный id счёта. Повторите попытку";
    }

    private String printBrokerAccountId(String token) {
        List<UserAccount> accounts;
        try {
            accounts = ApiManager.getBrokerAccounts(token);
        }  catch (ExecutionException e) {
            logger.warn("Превышен лимит запросов");
            return "Превышен лимит запросов";
        } catch (InterruptedException e) {
            logger.error("Соединение с Tinkoff API прервано");
            return "Соединение прервано";
        }

        if (accounts == null) {
            return "У вас нету брокерского аккаунта. Зарегистрируйте его в приложении, а потом повторите попытку";
        }
        StringBuilder message = new StringBuilder("Список выших брокерских счетов: \n\n");
        for (UserAccount account : accounts) {
            message.append(format(account));
        }
        message.append("<i>");
        message.append("*Отправьте id счёта следующим сообщением");
        message.append("</i>");

        return message.toString();
    }

    private String format(UserAccount account) {
        StringBuilder message = new StringBuilder();
        message.append("<b>");
        if (account.getBrokerAccountType().equals(BrokerAccountType.TINKOFF)) {
            message.append("Брокерский счёт");
        } else {
            message.append("Индивидуальный инвестиционный счёт");
        }
        message.append("</b>\n");
        message.append("<i>id:</i> <code>");
        message.append(account.getBrokerAccountId());
        message.append("</code>\n\n");

        return message.toString();
    }
}
