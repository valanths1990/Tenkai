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

package l2server.log;

import l2server.util.StringUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * @author zabbix
 *         Lets drink to code!
 */
public class AuditFormatter extends Formatter
{
	private static final String CRLF = "\r\n";
	private SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM H:mm:ss");

	@Override
	public String format(LogRecord record)
	{
		final Object[] params = record.getParameters();
		final StringBuilder output = StringUtil
				.startAppend(30 + record.getMessage().length() + (params == null ? 0 : params.length * 10), "[",
						dateFmt.format(new Date(record.getMillis())), "] ", record.getMessage());
		for (Object p : params)
		{
			if (p == null)
			{
				continue;
			}
			StringUtil.append(output, ", ", p.toString());
		}
		output.append(CRLF);
		return output.toString();
	}
}
