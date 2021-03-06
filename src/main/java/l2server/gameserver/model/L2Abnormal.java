/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package l2server.gameserver.model;

import l2server.gameserver.ThreadPoolManager;
import l2server.gameserver.TimeController;
import l2server.gameserver.datatables.SkillTable;
import l2server.gameserver.model.actor.L2Character;
import l2server.gameserver.model.actor.instance.L2PcInstance;
import l2server.gameserver.model.actor.instance.L2SummonInstance;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.AbnormalStatusUpdate;
import l2server.gameserver.network.serverpackets.AbnormalStatusUpdateFromTarget;
import l2server.gameserver.network.serverpackets.ExOlympiadSpelledInfo;
import l2server.gameserver.network.serverpackets.MagicSkillLaunched;
import l2server.gameserver.network.serverpackets.MagicSkillUse;
import l2server.gameserver.network.serverpackets.PartySpelled;
import l2server.gameserver.network.serverpackets.SystemMessage;
import l2server.gameserver.stats.Env;
import l2server.gameserver.stats.VisualEffect;
import l2server.gameserver.stats.funcs.Func;
import l2server.gameserver.stats.funcs.FuncTemplate;
import l2server.gameserver.templates.skills.L2AbnormalTemplate;
import l2server.gameserver.templates.skills.L2AbnormalType;
import l2server.gameserver.templates.skills.L2EffectType;
import l2server.gameserver.templates.skills.L2SkillTargetType;
import l2server.log.Log;

import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * This class ...
 *
 * @version $Revision: 1.1.2.1.2.12 $ $Date: 2005/04/11 10:06:07 $
 */
public class L2Abnormal
{

	public enum AbnormalState
	{
		CREATED, ACTING, FINISHING
	}

	private static final Func[] _emptyFunctionSet = new Func[0];

	//member _effector is the instance of L2Character that cast/used the spell/skill that is
	//causing this effect.  Do not confuse with the instance of L2Character that
	//is being affected by this effect.
	private final L2Character _effector;

	//member _effected is the instance of L2Character that was affected
	//by this effect.  Do not confuse with the instance of L2Character that
	//casted/used this effect.
	private final L2Character _effected;

	//the skill that was used.
	private final L2Skill _skill;

	private final boolean _isHerbEffect;

	//or the items that was used.
	//private final L2Item _item;

	// the current state
	private AbnormalState _state;

	// period, seconds
	private final int _duration;
	private int _periodStartTicks;
	private int _periodFirstTime;

	private L2AbnormalTemplate _template;

	// function templates
	private final FuncTemplate[] _funcTemplates;

	//initial count
	private int _totalCount;
	// counter
	private int _count;

	// visual effect
	private VisualEffect[] _visualEffect;
	// show icon
	private boolean _icon;
	// is selfeffect ?
	private boolean _isSelfEffect = false;
	// skill combo id
	private int _comboId = 0;

	public boolean preventExitUpdate;
	private int _strikes = 0;
	private int _blockedDamage = 0;
	private int _debuffBlocks = 0;

	private final class AbnormalTask implements Runnable
	{
		@Override
		public void run()
		{
			try
			{
				_periodFirstTime = 0;
				_periodStartTicks = TimeController.getGameTicks();
				scheduleEffect();
			}
			catch (Exception e)
			{
				Log.log(Level.SEVERE, "", e);
			}
		}
	}

	private ScheduledFuture<?> _currentFuture;

	/**
	 * The Identifier of the stack group
	 */
	private final String[] _stackType;

	/**
	 * The position of the effect in the stack group
	 */
	private final byte _stackLvl;

	private boolean _inUse = false;
	private boolean _startConditionsCorrect = true;

	private double _landRate;

	private L2Effect[] _effects;

	/**
	 * <font color="FF0000"><b>WARNING: scheduleEffect nolonger inside constructor</b></font><br>
	 * So you must call it explicitly
	 */
	public L2Abnormal(Env env, L2AbnormalTemplate template, L2Effect[] effects)
	{
		_state = AbnormalState.CREATED;
		_skill = env.skill;
		//_item = env._item == null ? null : env._item.getItem();
		_template = template;
		_effected = env.target;
		_effector = env.player;
		_funcTemplates = template.funcTemplates;
		_count = template.counter;
		_totalCount = _count;

		// Support for retail herbs duration when _effected has a Summon
		int temp = template.duration;

		if (_skill.getId() > 2277 && _skill.getId() < 2286 || _skill.getId() >= 2512 && _skill.getId() <= 2514)
		{
			if (_effected instanceof L2SummonInstance ||
					_effected instanceof L2PcInstance && !((L2PcInstance) _effected).getSummons().isEmpty())
			{
				temp /= 2;
			}
		}

		if (env.skillMastery)
		{
			temp *= 2;
		}

		_duration = temp;
		_visualEffect = template.visualEffect;
		_stackType = template.stackType;
		_stackLvl = template.stackLvl;
		_periodStartTicks = TimeController.getGameTicks();
		_periodFirstTime = 0;
		_icon = template.icon;
		_landRate = template.landRate;

		_isHerbEffect = _skill.getName().contains("Herb");
		_comboId = template.comboId;

		_effects = effects;
	}

	/**
	 * Special constructor to "steal" buffs. Must be implemented on
	 * every child class that can be stolen.<br><br>
	 * <p>
	 * <font color="FF0000"><b>WARNING: scheduleEffect nolonger inside constructor</b></font>
	 * <br>So you must call it explicitly
	 *
	 * @param env
	 * @param effect
	 */
	protected L2Abnormal(Env env, L2Abnormal effect)
	{
		_template = effect._template;
		_state = AbnormalState.CREATED;
		_skill = env.skill;
		_effected = env.target;
		_effector = env.player;
		_funcTemplates = _template.funcTemplates;
		_count = effect.getCount();
		_totalCount = _template.counter;
		_duration = _template.duration;
		_visualEffect = _template.visualEffect;
		_stackType = _template.stackType;
		_stackLvl = _template.stackLvl;
		_periodStartTicks = effect.getPeriodStartTicks();
		_periodFirstTime = effect.getTime();
		_icon = _template.icon;

		_isHerbEffect = _skill.getName().contains("Herb");

		_comboId = effect._comboId;

		/*
		 * Commented out by DrHouse:
		 * scheduleEffect can call onStart before effect is completly
		 * initialized on constructor (child classes constructor)
		 */
		//scheduleEffect();
	}

	public int getCount()
	{
		return _count;
	}

	public int getTotalCount()
	{
		return _totalCount;
	}

	public void setCount(int newcount)
	{
		_count = Math.min(newcount, _totalCount); // sanity check
	}

	public void setFirstTime(int newFirstTime)
	{
		_periodFirstTime = Math.min(newFirstTime, _duration);
		_periodStartTicks -= _periodFirstTime * TimeController.TICKS_PER_SECOND;
	}

	public boolean getShowIcon()
	{
		return _icon;
	}

	public int getDuration()
	{
		return _duration;
	}

	public int getTime()
	{
		return (TimeController.getGameTicks() - _periodStartTicks) / TimeController.TICKS_PER_SECOND;
	}

	/**
	 * Returns the elapsed time of the task.
	 *
	 * @return Time in seconds.
	 */
	public int getTaskTime()
	{
		if (_count == _totalCount)
		{
			return 0;
		}
		return Math.abs(_count - _totalCount + 1) * _duration + getTime() + 1;
	}

	public boolean getInUse()
	{
		return _inUse;
	}

	public boolean setInUse(boolean inUse)
	{
		_inUse = inUse;
		if (_inUse)
		{
			_startConditionsCorrect = onStart();
		}
		else
		{
			onExit();
		}

		return _startConditionsCorrect;
	}

	public String[] getStackType()
	{
		return _stackType;
	}

	public byte getStackLvl()
	{
		return _stackLvl;
	}

	public final L2Skill getSkill()
	{
		return _skill;
	}

	public final L2Character getEffector()
	{
		return _effector;
	}

	public final L2Character getEffected()
	{
		return _effected;
	}

	public boolean isSelfEffect()
	{
		return _isSelfEffect;
	}

	public void setSelfEffect()
	{
		_isSelfEffect = true;
	}

	public boolean isHerbEffect()
	{
		return _isHerbEffect;
	}

	private synchronized void startEffectTask()
	{
		if (_duration > 0)
		{
			stopEffectTask();
			final int initialDelay = Math.max((_duration - _periodFirstTime) * 1000, 5);
			if (_count > 1)
			{
				_currentFuture = ThreadPoolManager.getInstance()
						.scheduleEffectAtFixedRate(new AbnormalTask(), initialDelay, _duration * 1000);
			}
			else
			{
				_currentFuture = ThreadPoolManager.getInstance().scheduleEffect(new AbnormalTask(), initialDelay);
			}
		}
		if (_state == AbnormalState.ACTING)
		{
			if (isSelfEffectType())
			{
				_effector.addEffect(this);
			}
			else
			{
				_effected.addEffect(this);
			}
		}
	}

	/**
	 * Stop the L2Effect task and send Server->Client update packet.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Cancel the effect in the the abnormal effect map of the L2Character </li>
	 * <li>Stop the task of the L2Effect, remove it and update client magic icon </li><BR><BR>
	 */
	public final void exit()
	{
		exit(false);
	}

	public final void exit(boolean preventUpdate)
	{
		preventExitUpdate = preventUpdate;
		_state = AbnormalState.FINISHING;
		scheduleEffect();
	}

	/**
	 * Stop the task of the L2Effect, remove it and update client magic icon.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Cancel the task </li>
	 * <li>Stop and remove L2Effect from L2Character and update client magic icon </li><BR><BR>
	 */
	public final synchronized void stopEffectTask()
	{
		if (_currentFuture != null)
		{
			// Cancel the task
			_currentFuture.cancel(false);
			//ThreadPoolManager.getInstance().removeEffect(_currentTask);

			_currentFuture = null;

			if (isSelfEffectType() && getEffector() != null)
			{
				getEffector().removeEffect(this);
			}
			else if (getEffected() != null)
			{
				getEffected().removeEffect(this);
			}
		}
	}

	/**
	 * returns effect type
	 */
	public L2AbnormalType getType()
	{
		if (getTemplate().effectType != L2AbnormalType.NONE)
		{
			return getTemplate().effectType;
		}

		for (L2Effect e : _effects)
		{
			if (e.getAbnormalType() != L2AbnormalType.NONE)
			{
				return e.getAbnormalType();
			}
		}

		return L2AbnormalType.NONE;
	}

	public long getEffectMask()
	{
		long mask = 0L;
		for (L2Effect e : _effects)
		{
			mask |= e.getEffectMask();
		}

		return mask;
	}

	/**
	 * Notify started
	 */
	public boolean onStart()
	{
		if (_visualEffect != null)
		{
			for (VisualEffect ve : _visualEffect)
			{
				getEffected().startVisualEffect(ve);
			}
		}

		boolean canStart = true;
		boolean[] started = new boolean[_effects.length];
		int i = 0;
		for (L2Effect effect : _effects)
		{
			started[i] = effect.onStart();
			if (!started[i])
			{
				canStart = false;
			}

			i++;
		}

		if (!canStart)
		{
			i = 0;
			for (L2Effect effect : _effects)
			{
				if (started[i])
				{
					effect.onExit();
				}

				i++;
			}

			if (_visualEffect != null)
			{
				for (VisualEffect ve : _visualEffect)
				{
					getEffected().stopVisualEffect(ve);
				}
			}
		}

		_strikes = 0;
		_debuffBlocks = 0;

		return canStart;
	}

	/**
	 * Cancel the effect in the the abnormal effect map of the effected L2Character.<BR><BR>
	 */
	public void onExit()
	{
		for (L2Effect effect : _effects)
		{
			effect.onExit();
		}

		if (_visualEffect != null)
		{
			for (VisualEffect ve : _visualEffect)
			{
				getEffected().stopVisualEffect(ve);
			}
		}
	}

	/**
	 * Cancel the effect in the the abnormal effect map of the effected L2Character.<BR><BR>
	 */
	public boolean onActionTime()
	{
		boolean toReturn = true;
		for (L2Effect effect : _effects)
		{
			if (!effect.onActionTime())
			{
				toReturn = false;
			}
		}

		return toReturn;
	}

	public final void scheduleEffect()
	{
		switch (_state)
		{
			case CREATED:
			{
				_state = AbnormalState.ACTING;

				if (_skill.isOffensive() && _icon && getEffected() instanceof L2PcInstance)
				{
					SystemMessage smsg = SystemMessage.getSystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT);
					smsg.addSkillName(_skill);
					getEffected().sendPacket(smsg);
				}

				if (_duration != 0)
				{
					startEffectTask();
					return;
				}
				// effects not having count or period should start
				_startConditionsCorrect = onStart();
			}
			case ACTING:
			{
				if (_count > 0)
				{
					_count--;
					if (getInUse())
					{ // effect has to be in use
						if (onActionTime() && _startConditionsCorrect && _count > 0)
						{
							return; // false causes effect to finish right away
						}
					}
					else if (_count > 0)
					{ // do not finish it yet, in case reactivated
						return;
					}
				}
				_state = AbnormalState.FINISHING;
			}
			case FINISHING:
			{
				//If the time left is equal to zero, send the message
				if (_count == 0 && _icon && getEffected() instanceof L2PcInstance)
				{
					SystemMessage smsg3 = SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_WORN_OFF);
					smsg3.addSkillName(_skill);
					getEffected().sendPacket(smsg3);
				}
				// if task is null - stopEffectTask does not remove effect
				if (_currentFuture == null && getEffected() != null)
				{
					getEffected().removeEffect(this);
				}
				// Stop the task of the L2Effect, remove it and update client magic icon
				stopEffectTask();

				// Cancel the effect in the the abnormal effect map of the L2Character
				if (getInUse() || !(_count > 1 || _duration > 0))
				{
					if (_startConditionsCorrect)
					{
						onExit();
					}
				}

				if (_skill.getAfterEffectId() > 0)
				{
					L2Skill skill =
							SkillTable.getInstance().getInfo(_skill.getAfterEffectId(), _skill.getAfterEffectLvl());
					if (skill != null)
					{
						getEffected().broadcastPacket(
								new MagicSkillUse(_effected, skill.getId(), skill.getLevelHash(), 0, 0));
						getEffected().broadcastPacket(
								new MagicSkillLaunched(_effected, skill.getId(), skill.getLevelHash()));
						skill.getEffects(getEffected(), getEffected());
					}
				}

				if (_skill.getId() == 14571 && getEffected() instanceof L2PcInstance)
				{
					((L2PcInstance) getEffected()).decreaseBreathOfShilenDebuffLevel();
				}
				if (_skill.getId() == 1570 && getEffected() instanceof L2PcInstance &&
						((L2PcInstance) getEffected()).hasIdentityCrisis())
				{
					((L2PcInstance) getEffected()).setHasIdentityCrisis(false);
				}
			}
		}
	}

	public Func[] getStatFuncs()
	{
		if (_funcTemplates == null)
		{
			return _emptyFunctionSet;
		}
		ArrayList<Func> funcs = new ArrayList<>(_funcTemplates.length);

		Env env = new Env();
		env.player = getEffector();
		env.target = getEffected();
		env.skill = getSkill();
		Func f;

		for (FuncTemplate t : _funcTemplates)
		{
			f = t.getFunc(this); // effect is owner
			if (f != null)
			{
				funcs.add(f);
			}
		}
		if (funcs.isEmpty())
		{
			return _emptyFunctionSet;
		}

		return funcs.toArray(new Func[funcs.size()]);
	}

	public final void addIcon(AbnormalStatusUpdate mi)
	{
		if (_state != AbnormalState.ACTING)
		{
			return;
		}

		final ScheduledFuture<?> future = _currentFuture;
		final L2Skill sk = getSkill();

		int levelHash = getLevelHash();
		if (sk.getId() >= 11139 && sk.getId() <= 11145)
		{
			levelHash = getLevel();
		}
		if (_totalCount > 1)
		{
			mi.addEffect(sk.getId(), levelHash, _comboId,
					(_count - 1) * _duration * 1000 + (int) future.getDelay(TimeUnit.MILLISECONDS));
		}
		else if (future != null)
		{
			mi.addEffect(sk.getId(), levelHash, _comboId, (int) future.getDelay(TimeUnit.MILLISECONDS));
		}
		else if (_duration == -1)
		{
			mi.addEffect(sk.getId(), levelHash, _comboId, _duration);
		}
	}

	public final void addIcon(AbnormalStatusUpdateFromTarget mi)
	{
		if (_state != AbnormalState.ACTING)
		{
			return;
		}

		final ScheduledFuture<?> future = _currentFuture;
		final L2Skill sk = getSkill();
		if (sk == null || _effector == null || mi == null || future == null)
		{
			return;
		}

		int levelHash = getLevelHash();
		if (sk.getId() >= 11139 && sk.getId() <= 11145)
		{
			levelHash = getLevel();
		}
		if (_totalCount > 1)
		{
			mi.addEffect(sk.getId(), levelHash, _comboId,
					(_count - 1) * _duration * 1000 + (int) future.getDelay(TimeUnit.MILLISECONDS),
					_effector.getObjectId());
		}
		else if (_effector != null)
		{
			mi.addEffect(sk.getId(), levelHash, _comboId, (int) future.getDelay(TimeUnit.MILLISECONDS),
					_effector.getObjectId());
		}
		else if (_duration == -1)
		{
			mi.addEffect(sk.getId(), levelHash, _comboId, _duration, _effector.getObjectId());
		}
	}

	public final void addPartySpelledIcon(PartySpelled ps)
	{
		if (_state != AbnormalState.ACTING)
		{
			return;
		}

		final ScheduledFuture<?> future = _currentFuture;
		final L2Skill sk = getSkill();
		if (future != null)
		{
			ps.addPartySpelledEffect(sk.getId(), getLevelHash(), (int) future.getDelay(TimeUnit.MILLISECONDS));
		}
		else if (_duration == -1)
		{
			ps.addPartySpelledEffect(sk.getId(), getLevelHash(), _duration);
		}
	}

	public final void addOlympiadSpelledIcon(ExOlympiadSpelledInfo os)
	{
		if (_state != AbnormalState.ACTING)
		{
			return;
		}

		final ScheduledFuture<?> future = _currentFuture;
		final L2Skill sk = getSkill();
		if (future != null)
		{
			os.addEffect(sk.getId(), getLevelHash(), (int) future.getDelay(TimeUnit.MILLISECONDS));
		}
		else if (_duration == -1)
		{
			os.addEffect(sk.getId(), getLevelHash(), _duration);
		}
	}

	public int getLevel()
	{
		return getSkill().getLevel();
	}

	public int getEnchantRouteId()
	{
		return getSkill().getEnchantRouteId();
	}

	public int getEnchantLevel()
	{
		return getSkill().getEnchantLevel();
	}

	public int getLevelHash()
	{
		return getSkill().getLevelHash();
	}

	public int getPeriodStartTicks()
	{
		return _periodStartTicks;
	}

	public L2AbnormalTemplate getTemplate()
	{
		return _template;
	}

	public double getLandRate()
	{
		return _landRate;
	}

	public int getComboId()
	{
		return _comboId;
	}

	public L2Effect[] getEffects()
	{
		return _effects;
	}

	public boolean canBeStolen()
	{
		return !(!effectCanBeStolen() || getType() == L2AbnormalType.MUTATE || getSkill().isPassive() ||
				getSkill().getTargetType() == L2SkillTargetType.TARGET_SELF || getSkill().isToggle() ||
				getSkill().isDebuff() || getSkill().isHeroSkill() || getSkill().getTransformId() > 0
				//|| (this.getSkill().isGMSkill() && getEffected().getInstanceId() == 0)
				|| getSkill().isPotion() && getSkill().getId() != 2274 && getSkill().getId() != 2341
				// Hardcode for now :<
				|| isHerbEffect() || !getSkill().canBeDispeled());
	}

	public boolean canBeShared()
	{
		return !(!effectCanBeStolen() || getType() == L2AbnormalType.MUTATE || getSkill().isPassive() ||
				getSkill().isToggle() || getSkill().isDebuff()
				//|| (this.getSkill().isGMSkill() && getEffected().getInstanceId() == 0)
				|| !getSkill().canBeDispeled());
	}

	/**
	 * Return true if effect itself can be stolen
	 *
	 * @return
	 */
	protected boolean effectCanBeStolen()
	{
		for (L2Effect effect : _effects)
		{
			if (!effect.effectCanBeStolen())
			{
				return false;
			}
		}

		return true;
	}

	@Override
	public String toString()
	{
		return "L2Effect [_skill=" + _skill + ", _state=" + _state + ", _duration=" + _duration + "]";
	}

	public boolean isSelfEffectType()
	{
		for (L2Effect effect : _effects)
		{
			if (effect.isSelfEffectType())
			{
				return true;
			}
		}

		return false;
	}

	public boolean isRemovedOnDamage(int damage)
	{
		if (damage > 0)
		{
			_strikes++;
			_blockedDamage += damage;
		}

		return getSkill().isRemovedOnDamage() || (getEffectMask() & L2EffectType.SLEEP.getMask()) != 0 ||
				(getEffectMask() & L2EffectType.FEAR.getMask()) != 0 ||
				getSkill().getStrikesToRemove() > 0 && (damage == 0 || _strikes >= getSkill().getStrikesToRemove()) ||
				getSkill().getDamageToRemove() > 0 && (damage == 0 || _blockedDamage >= getSkill().getDamageToRemove());
	}

	public boolean isRemovedOnDebuffBlock(boolean onDebuffBlock)
	{
		if (getSkill().isRemovedOnDebuffBlock())
		{
			if (onDebuffBlock && getSkill().getDebuffBlocksToRemove() > 0)
			{
				_debuffBlocks++;
				return _debuffBlocks >= getSkill().getDebuffBlocksToRemove();
			}

			return true;
		}

		return false;
	}
}
