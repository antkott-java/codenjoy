package com.codenjoy.dojo.minesweeper.client.ai;

/*-
 * #%L
 * Codenjoy - it's a dojo-like platform from developers to developers.
 * %%
 * Copyright (C) 2016 Codenjoy
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */


import com.codenjoy.dojo.client.Solver;
import com.codenjoy.dojo.client.WebSocketRunner;
import com.codenjoy.dojo.minesweeper.client.Board;
import com.codenjoy.dojo.services.Dice;
import com.codenjoy.dojo.services.RandomDice;

public class Vaa25Solver extends YourDirectionSolver implements Solver<Board> {

    public Vaa25Solver(String name, Dice dice) {
        super(dice, name);
    }

    @Override
    public String get(Board board) {
        return super.get(new BoardAdapter(board));
    }

    public static void main(String[] args) {
//        LocalGameRunner.run(new GameRunner(),
//                new Vaa25Solver("test"),
//                new Board());
        start(WebSocketRunner.DEFAULT_USER, WebSocketRunner.Host.LOCAL, new RandomDice());
    }

    public static void start(String name, WebSocketRunner.Host host, Dice dice) {
        WebSocketRunner.run(host,
                name,
                null,
                new Vaa25Solver(name, dice),
                new Board());
    }

}
