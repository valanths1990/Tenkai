package l2server.gameserver.handler;

import l2server.L2DatabaseFactory;
import l2server.gameserver.model.DailyMissionDataHolder;
import l2server.gameserver.model.DailyMissionPlayerEntry;
import l2server.gameserver.model.DailyMissionStatus;
import l2server.gameserver.model.actor.instance.L2PcInstance;
import l2server.gameserver.model.event.ListenersContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractDailyMissionHandler extends ListenersContainer
{
    protected Logger LOGGER = Logger.getLogger(getClass().getName());

    private final Map<Integer, DailyMissionPlayerEntry> _entries = new ConcurrentHashMap<>();
    private final DailyMissionDataHolder _holder;

    protected AbstractDailyMissionHandler(DailyMissionDataHolder holder)
    {
        _holder = holder;
        init();
    }

    public DailyMissionDataHolder getHolder()
    {
        return _holder;
    }

    public abstract boolean isAvailable(L2PcInstance player);

    public abstract void init();

    public int getStatus(L2PcInstance player)
    {
        final DailyMissionPlayerEntry entry = getPlayerEntry(player.getObjectId(), false);
        return entry != null ? entry.getStatus().getClientId() : DailyMissionStatus.NOT_AVAILABLE.getClientId();
    }

    public int getProgress(L2PcInstance player)
    {
        final DailyMissionPlayerEntry entry = getPlayerEntry(player.getObjectId(), false);
        return entry != null ? entry.getProgress() : 0;
    }

    public synchronized void reset()
    {
        if (!_holder.dailyReset())
        {
            return;
        }

        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM character_daily_rewards WHERE rewardId = ? AND status = ?"))
        {
            ps.setInt(1, _holder.getId());
            ps.setInt(2, DailyMissionStatus.COMPLETED.getClientId());
            ps.execute();
        }
        catch (SQLException e)
        {
            LOGGER.log(Level.WARNING, "Error while clearing data for: " + getClass().getSimpleName(), e);
        }
        finally
        {
            _entries.clear();
        }
    }

    public boolean requestReward(L2PcInstance player)
    {
        if (isAvailable(player))
        {
            giveRewards(player);

            final DailyMissionPlayerEntry entry = getPlayerEntry(player.getObjectId(), true);
            entry.setStatus(DailyMissionStatus.COMPLETED);
            entry.setLastCompleted(System.currentTimeMillis());
            storePlayerEntry(entry);

            return true;
        }
        return false;
    }

    protected void giveRewards(L2PcInstance player)
    {
        _holder.getRewards().forEach(i -> player.addItem("One Day Reward", i.getId(), i.getCount(), player, true));
    }

    protected void storePlayerEntry(DailyMissionPlayerEntry entry)
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("REPLACE INTO character_daily_rewards (charId, rewardId, status, progress, lastCompleted) VALUES (?, ?, ?, ?, ?)"))
        {
            ps.setInt(1, entry.getObjectId());
            ps.setInt(2, entry.getRewardId());
            ps.setInt(3, entry.getStatus().getClientId());
            ps.setInt(4, entry.getProgress());
            ps.setLong(5, entry.getLastCompleted());
            ps.execute();

            // Cache if not exists
            _entries.computeIfAbsent(entry.getObjectId(), id -> entry);
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Error while saving reward " + entry.getRewardId() + " for player: " + entry.getObjectId() + " in database: ", e);
        }
    }

    protected DailyMissionPlayerEntry getPlayerEntry(int objectId, boolean createIfNone)
    {
        final DailyMissionPlayerEntry existingEntry = _entries.get(objectId);
        if (existingEntry != null)
        {
            return existingEntry;
        }

        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM character_daily_rewards WHERE charId = ? AND rewardId = ?"))
        {
            ps.setInt(1, objectId);
            ps.setInt(2, _holder.getRewardId());
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    final DailyMissionPlayerEntry entry = new DailyMissionPlayerEntry(rs.getInt("charId"), rs.getInt("rewardId"), rs.getInt("status"), rs.getInt("progress"), rs.getLong("lastCompleted"));
                    _entries.put(objectId, entry);
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Error while loading reward " + _holder.getId() + " for player: " + objectId + " in database: ", e);
        }


        DailyMissionPlayerEntry d = _entries.get(objectId);
        if (d == null && createIfNone)
        {
            final DailyMissionPlayerEntry entry = new DailyMissionPlayerEntry(objectId, _holder.getRewardId());
            _entries.put(objectId, entry);
            return entry;
        } else if (d != null) {
            return d;
        }
        return null;
    }
}
