import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";

import { formatCurrency } from "@/lib/format";
import type { ReportByCategoryDTO } from "@/lib/types";

interface CategoryChartProps {
  colors: string[];
  data: ReportByCategoryDTO[];
}

export function CategoryChart({ colors, data }: CategoryChartProps) {
  return (
    <ResponsiveContainer height="100%" width="100%">
      <PieChart>
        <Pie
          data={data}
          dataKey="total"
          innerRadius={58}
          nameKey="categoryName"
          outerRadius={92}
          paddingAngle={2}
        >
          {data.map((entry, index) => (
            <Cell fill={colors[index % colors.length]} key={`${entry.categoryName}-${entry.total}`} />
          ))}
        </Pie>
        <Tooltip formatter={(value) => formatCurrency(Number(value))} />
      </PieChart>
    </ResponsiveContainer>
  );
}
