#!/bin/bash


TODO_FILE="todo.txt"


# Create todo file if it doesn't exist

[ ! -f "$TODO_FILE" ] && touch "$TODO_FILE"


show_help() {

    echo "Usage: ./todo.sh [command] [arguments]"

    echo ""

    echo "Commands:"

    echo "  add <task>    Add a new task"

    echo "  list          List all pending tasks"

    echo "  done <id>     Mark a task as done (remove it by its number)"

    echo "  clear         Clear all tasks"

}


case "$1" in

    add)

        if [ -z "$2" ]; then

            echo "Error: Task description cannot be empty."

            exit 1

        # Shift past the 'add' command to capture the full multi-word task description

        fi

        shift

        echo "$*" >> "$TODO_FILE"

        echo "Added: $*"

        ;;

    list)

        if [ ! -s "$TODO_FILE" ]; then

            echo "Your todo list is empty!"

            exit 0

        fi

        echo "--- Your Todo List ---"

        # Print line numbers alongside tasks

        nl -w2 -s'. ' "$TODO_FILE"

        ;;

    done)

        if [ -z "$2" ] || ! [[ "$2" =~ ^[0-9]+$ ]]; then

            echo "Error: Please provide a valid task number."

            exit 1

        fi

        # Delete the specified line number from the todo file

        sed -i "${2}d" "$TODO_FILE"

        echo "Completed/Removed task #$2"

        ;;

    clear)

        > "$TODO_FILE"

        echo "Todo list cleared!"

        ;;

    *)

        show_help

        ;;

esac
