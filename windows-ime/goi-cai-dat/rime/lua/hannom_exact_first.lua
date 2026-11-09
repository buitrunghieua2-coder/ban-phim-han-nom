-- Bàn Phím Hán Nôm: when the user typed a tone key or a vowel mark (precise input,
-- e.g. "hans", "cos", "vieetj"), list the characters whose reading matches exactly
-- before those reached through the no-diacritics shortcuts (e.g. hắn for "hans").
-- With the "go_du_dau" switch on (default) every input counts as precise: typing "to" lists
-- 𡚢 (to) before tô / tơ / tổ / tớ…; turn it off (F4 menu) for frequency order on plain input.
-- Rime shows the reading as a comment only when it differs from what was typed,
-- so an empty comment means an exact match.
local M = {}

local function precise(input)
  local last = (input or ""):match("([%a]+)$")
  if not last then return false end
  last = last:lower()
  return last:find("[sfrxj]$") ~= nil
      or last:find("aa") ~= nil or last:find("ee") ~= nil or last:find("oo") ~= nil
      or last:find("w") ~= nil or last:find("^dd") ~= nil
end

function M.func(input, env)
  if not env.engine.context:get_option("go_du_dau") and not precise(env.engine.context.input) then
    for cand in input:iter() do yield(cand) end
    return
  end
  local later, n, full = {}, 0, nil
  for cand in input:iter() do
    n = n + 1
    if full == nil then full = cand._end end   -- the first candidate spans the whole input
    if n > 120 then
      for _, c in ipairs(later) do yield(c) end
      later = {}
      yield(cand)
    elseif cand.type ~= "hannom_preview" and cand._end == full
        and (cand.comment == nil or cand.comment == "") then
      yield(cand)
    else
      table.insert(later, cand)
    end
  end
  for _, c in ipairs(later) do yield(c) end
end

return M
